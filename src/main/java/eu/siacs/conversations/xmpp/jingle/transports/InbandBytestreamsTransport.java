package eu.siacs.conversations.xmpp.jingle.transports;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.stanzas.IbbTransportInfo;
import im.conversations.android.xmpp.IqProcessingException;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.ibb.Close;
import im.conversations.android.xmpp.model.ibb.Data;
import im.conversations.android.xmpp.model.ibb.InBandByteStream;
import im.conversations.android.xmpp.model.ibb.Open;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InbandBytestreamsTransport implements Transport {

    private static final int DEFAULT_BLOCK_SIZE = 8192;

    private final PipedInputStream pipedInputStream = new PipedInputStream(DEFAULT_BLOCK_SIZE);
    private final PipedOutputStream pipedOutputStream = new PipedOutputStream();
    private final CountDownLatch terminationLatch = new CountDownLatch(1);

    private final XmppConnection xmppConnection;

    private final Jid with;

    private final boolean initiator;

    private final String streamId;

    private int blockSize;
    private Callback transportCallback;
    private final BlockSender blockSender;

    private final Thread blockSenderThread;

    private final AtomicBoolean isReceiving = new AtomicBoolean(false);

    public InbandBytestreamsTransport(
            final XmppConnection xmppConnection, final Jid with, final boolean initiator) {
        this(xmppConnection, with, initiator, UUID.randomUUID().toString(), DEFAULT_BLOCK_SIZE);
    }

    public InbandBytestreamsTransport(
            final XmppConnection xmppConnection,
            final Jid with,
            final boolean initiator,
            final String streamId,
            final int blockSize) {
        this.xmppConnection = xmppConnection;
        this.with = with;
        this.initiator = initiator;
        this.streamId = streamId;
        this.blockSize = Math.min(DEFAULT_BLOCK_SIZE, blockSize);
        this.blockSender =
                new BlockSender(xmppConnection, with, streamId, this.blockSize, pipedInputStream);
        this.blockSenderThread = new Thread(blockSender);
    }

    public void setTransportCallback(final Callback callback) {
        this.transportCallback = callback;
    }

    public String getStreamId() {
        return this.streamId;
    }

    public void connect() {
        if (initiator) {
            openInBandTransport();
        }
    }

    @Override
    public CountDownLatch getTerminationLatch() {
        return this.terminationLatch;
    }

    private void openInBandTransport() {
        final var iqPacket = new Iq(Iq.Type.SET);
        iqPacket.setTo(with);
        final var open = iqPacket.addExtension(new Open());
        open.setBlockSize(this.blockSize);
        open.setSid(this.streamId);
        Log.d(Config.LOGTAG, "sending ibb open");
        Log.d(Config.LOGTAG, iqPacket.toString());
        final var future = xmppConnection.sendIqPacket(iqPacket);
        Futures.addCallback(
                future,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(Iq result) {
                        Log.d(Config.LOGTAG, "ibb open was accepted");
                        InbandBytestreamsTransport.this.transportCallback.onTransportEstablished();
                        InbandBytestreamsTransport.this.blockSenderThread.start();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not open IBB transport", t);
                        InbandBytestreamsTransport.this.transportCallback.onTransportSetupFailed();
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void deliverPacket(final Jid from, final InBandByteStream inBandByteStream)
            throws IqProcessingException {
        if (from == null || !from.equals(with)) {
            Log.d(
                    Config.LOGTAG,
                    "ibb packet received from wrong address. was " + from + " expected " + with);
            throw new IqProcessingException(new Condition.ItemNotFound(), "Session not found");
        }
        switch (inBandByteStream) {
            case Open open -> receiveOpen(open);
            case Data data -> receiveData(data);
            case Close ignored -> receiveClose();
            default ->
                    throw new IqProcessingException(
                            new Condition.BadRequest(), "Invalid IBB packet type");
        }
        ;
    }

    private void receiveData(final Data data) throws IqProcessingException {
        final byte[] buffer;
        try {
            buffer = data.asBytes();
        } catch (final IllegalStateException e) {
            throw new IqProcessingException(
                    new Condition.BadRequest(), "received invalid ibb data", e);
        }
        if (buffer.length > this.blockSize) {
            throw new IqProcessingException(
                    new Condition.BadRequest(), "block size larger than expected");
        }
        Log.d(Config.LOGTAG, "ibb received " + buffer.length + " bytes");
        try {
            pipedOutputStream.write(buffer);
            pipedOutputStream.flush();
        } catch (final IOException e) {
            throw new IqProcessingException(
                    new Condition.ResourceConstraint(), "Unable to receive IBB data", e);
        }
    }

    private void receiveClose() throws IqProcessingException {
        if (this.isReceiving.compareAndSet(true, false)) {
            try {
                this.pipedOutputStream.close();
            } catch (final IOException e) {
                throw new IqProcessingException(
                        new Condition.ResourceConstraint(), "Could not close stream", e);
            }
        } else {
            throw new IqProcessingException(
                    new Condition.BadRequest(), "received ibb close but was not receiving");
        }
    }

    private void receiveOpen(final Open open) throws IqProcessingException {
        Log.d(Config.LOGTAG, "receiveOpen()");
        final var openBlockSize = open.getBlockSize();
        if (this.blockSize != openBlockSize) {
            throw new IqProcessingException(
                    new Condition.BadRequest(),
                    String.format(
                            "open block size (%d) does not matched configured block size (%d)",
                            openBlockSize, this.blockSize));
        }
        if (this.isReceiving.get()) {
            throw new IqProcessingException(
                    new Condition.BadRequest(),
                    "ibb received open even though we were already open");
        }
        this.isReceiving.set(true);
        transportCallback.onTransportEstablished();
    }

    public void terminate() {
        Log.d(Config.LOGTAG, "IbbTransport.terminate()");
        final var iqPacket = new Iq(Iq.Type.SET);
        iqPacket.setTo(with);
        final var close = iqPacket.addExtension(new Close());
        close.setSid(this.streamId);
        this.terminationLatch.countDown();
        this.blockSender.close();
        this.blockSenderThread.interrupt();
        closeQuietly(this.pipedOutputStream);
    }

    private static void closeQuietly(final OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (final IOException ignored) {

        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final var outputStream = new PipedOutputStream();
        this.pipedInputStream.connect(outputStream);
        return outputStream;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final var inputStream = new PipedInputStream();
        this.pipedOutputStream.connect(inputStream);
        return inputStream;
    }

    @Override
    public ListenableFuture<TransportInfo> asTransportInfo() {
        return Futures.immediateFuture(
                new TransportInfo(new IbbTransportInfo(streamId, blockSize), null));
    }

    @Override
    public ListenableFuture<InitialTransportInfo> asInitialTransportInfo() {
        return Futures.immediateFuture(
                new InitialTransportInfo(
                        UUID.randomUUID().toString(),
                        new IbbTransportInfo(streamId, blockSize),
                        null));
    }

    public void setPeerBlockSize(long peerBlockSize) {
        this.blockSize = Math.min(Ints.saturatedCast(peerBlockSize), DEFAULT_BLOCK_SIZE);
        if (this.blockSize < DEFAULT_BLOCK_SIZE) {
            Log.d(Config.LOGTAG, "peer reconfigured IBB block size to " + this.blockSize);
        }
        this.blockSender.setBlockSize(this.blockSize);
    }

    private static class BlockSender implements Runnable, Closeable {

        private final XmppConnection xmppConnection;

        private final Jid with;
        private final String streamId;

        private int blockSize;
        private final PipedInputStream inputStream;
        private final Semaphore semaphore = new Semaphore(3);
        private final AtomicInteger sequencer = new AtomicInteger();
        private final AtomicBoolean isSending = new AtomicBoolean(true);

        private BlockSender(
                XmppConnection xmppConnection,
                final Jid with,
                String streamId,
                int blockSize,
                PipedInputStream inputStream) {
            this.xmppConnection = xmppConnection;
            this.with = with;
            this.streamId = streamId;
            this.blockSize = blockSize;
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            final var buffer = new byte[blockSize];
            try {
                while (isSending.get()) {
                    final int count = this.inputStream.read(buffer);
                    if (count < 0) {
                        Log.d(Config.LOGTAG, "block sender reached EOF");
                        return;
                    }
                    this.semaphore.acquire();
                    final var block = new byte[count];
                    System.arraycopy(buffer, 0, block, 0, block.length);
                    sendIbbBlock(sequencer.getAndIncrement(), block);
                }
            } catch (final InterruptedException | InterruptedIOException e) {
                if (isSending.get()) {
                    Log.w(Config.LOGTAG, "IbbBlockSender got interrupted while sending", e);
                }
            } catch (final IOException e) {
                Log.d(Config.LOGTAG, "block sender terminated", e);
            } finally {
                Closeables.closeQuietly(inputStream);
            }
        }

        private void sendIbbBlock(final int sequence, final byte[] block) {
            Log.d(Config.LOGTAG, "sending ibb block #" + sequence + " " + block.length + " bytes");
            final var iqPacket = new Iq(Iq.Type.SET);
            iqPacket.setTo(with);
            final var data = iqPacket.addExtension(new Data());
            data.setSid(this.streamId);
            data.setSequence(sequence);
            data.setContent(block);
            final var future = this.xmppConnection.sendIqPacket(iqPacket);
            Futures.addCallback(
                    future,
                    new FutureCallback<Iq>() {
                        @Override
                        public void onSuccess(final Iq result) {
                            semaphore.release();
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(
                                    Config.LOGTAG,
                                    "received iq error in response to data block #" + sequence,
                                    t);
                            isSending.set(false);
                            semaphore.release();
                        }
                    },
                    MoreExecutors.directExecutor());
        }

        @Override
        public void close() {
            this.isSending.set(false);
        }

        public void setBlockSize(final int blockSize) {
            this.blockSize = blockSize;
        }
    }
}

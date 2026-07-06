package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.databinding.DataBindingUtil;
import com.google.common.base.Stopwatch;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRecordingBinding;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.TimeFrameUtils;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RecordingActivity extends BaseActivity implements View.OnClickListener {

    public static String EXTRA_AUTO_SEND_RECORDING = "auto_send_recording";

    private ActivityRecordingBinding binding;

    private MediaRecorder mRecorder;
    private Stopwatch stopwatch;

    private final CountDownLatch outputFileWrittenLatch = new CountDownLatch(1);

    private final Handler mHandler = new Handler();
    private final Runnable mTickExecutor =
            new Runnable() {
                @Override
                public void run() {
                    tick();
                    mHandler.postDelayed(mTickExecutor, 100);
                }
            };

    private boolean autoSendRecording = false;
    private File mOutputFile;
    private FileObserver mFileObserver;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final var appSettings = new AppSettings(this);
        this.autoSendRecording = appSettings.isAutoSendRecording();
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_recording);
        this.binding.timer.setOnClickListener(
                v -> {
                    onPauseContinue();
                });
        this.binding.cancelButton.setOnClickListener(this);
        if (this.autoSendRecording) {
            this.binding.shareButton.setText(R.string.send);
            this.binding.shareButton.setIconResource(R.drawable.ic_send_24dp);
        } else {
            this.binding.shareButton.setText(R.string.attach);
            this.binding.shareButton.setIconResource(R.drawable.ic_check_24dp);
        }
        this.binding.shareButton.setOnClickListener(this);
        this.setFinishOnTouchOutside(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onPauseContinue() {
        final var recorder = this.mRecorder;
        final var stopwatch = this.stopwatch;
        if (recorder == null
                || stopwatch == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        if (stopwatch.isRunning()) {
            try {
                recorder.pause();
                stopwatch.stop();
            } catch (final IllegalStateException e) {
                Log.d(Config.LOGTAG, "could not pause recording", e);
            }
        } else {
            try {
                recorder.resume();
                stopwatch.start();
            } catch (final IllegalStateException e) {
                Log.d(Config.LOGTAG, "could not resume recording", e);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!startRecording()) {
            this.binding.shareButton.setEnabled(false);
            this.binding.timer.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            // TODO reset font family. make red?
            this.binding.timer.setText(R.string.unable_to_start_recording);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRecorder != null) {
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
        }
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
        }
    }

    private boolean startRecording() {
        mRecorder = new MediaRecorder();
        stopwatch = Stopwatch.createUnstarted();
        try {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        } catch (final RuntimeException e) {
            Log.e(Config.LOGTAG, "could not set audio source", e);
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mRecorder.setPrivacySensitive(true);
        }
        final int outputFormat;
        if (Config.USE_OPUS_VOICE_MESSAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputFormat = MediaRecorder.OutputFormat.OGG;
            mRecorder.setOutputFormat(outputFormat);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            mRecorder.setAudioEncodingBitRate(32_000);
        } else {
            outputFormat = MediaRecorder.OutputFormat.MPEG_4;
            mRecorder.setOutputFormat(outputFormat);
            // Changing these three settings for AAC sensitive devices for Android<=13 might
            // lead to sporadically truncated (cut-off) voice messages.
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
            mRecorder.setAudioSamplingRate(24_000);
            mRecorder.setAudioEncodingBitRate(28_000);
        }
        setupOutputFile(outputFormat);
        mRecorder.setOutputFile(mOutputFile.getAbsolutePath());

        try {
            mRecorder.prepare();
            mRecorder.start();
            stopwatch.start();
            mHandler.postDelayed(mTickExecutor, 100);
            Log.d(Config.LOGTAG, "started recording to " + mOutputFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "prepare() failed ", e);
            return false;
        }
    }

    protected void stopRecording(final boolean saveFile) {
        try {
            mRecorder.stop();
            mRecorder.release();
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "could not save recording", e);
            if (saveFile) {
                Toast.makeText(this, R.string.unable_to_save_recording, Toast.LENGTH_SHORT).show();
                return;
            }
        } finally {
            mRecorder = null;
        }
        if (!saveFile && mOutputFile != null) {
            if (mOutputFile.delete()) {
                Log.d(Config.LOGTAG, "deleted canceled recording");
            }
        }
        if (saveFile) {
            new Thread(new Finisher(outputFileWrittenLatch, mOutputFile, autoSendRecording, this))
                    .start();
        }
    }

    private static class Finisher implements Runnable {

        private final CountDownLatch latch;
        private final File outputFile;
        private final boolean autoSendRecording;
        private final WeakReference<Activity> activityReference;

        private Finisher(
                final CountDownLatch latch,
                final File outputFile,
                final boolean autoSendRecording,
                final Activity activity) {
            this.latch = latch;
            this.outputFile = outputFile;
            this.autoSendRecording = autoSendRecording;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            try {
                if (!latch.await(8, TimeUnit.SECONDS)) {
                    Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                }
            } catch (final InterruptedException e) {
                Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
            }
            final Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> finish(activity));
        }

        private void finish(final Activity activity) {
            final var intent = new Intent();
            intent.setData(Uri.fromFile(outputFile));
            intent.putExtra(EXTRA_AUTO_SEND_RECORDING, autoSendRecording);
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }
    }

    private void setupOutputFile(final int outputFormat) {
        mOutputFile = new FileBackend.Cache(this).recording(outputFormat);
        final File parentDirectory = mOutputFile.getParentFile();
        if (Objects.requireNonNull(parentDirectory).mkdirs()) {
            Log.d(Config.LOGTAG, "created " + parentDirectory.getAbsolutePath());
        }
        setupFileObserver(parentDirectory);
    }

    private void setupFileObserver(final File directory) {
        mFileObserver =
                new FileObserver(directory.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, String s) {
                        if (s != null
                                && s.equals(mOutputFile.getName())
                                && event == FileObserver.CLOSE_WRITE) {
                            outputFileWrittenLatch.countDown();
                        }
                    }
                };
        mFileObserver.startWatching();
    }

    private void tick() {
        this.binding.timer.setText(
                TimeFrameUtils.formatElapsedTime(stopwatch.elapsed(TimeUnit.MILLISECONDS), true));
    }

    @Override
    public void onClick(final View view) {
        if (view.getId() == R.id.cancel_button) {
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
            setResult(RESULT_CANCELED);
            finish();
        } else if (view.getId() == R.id.share_button) {
            this.binding.timer.setOnClickListener(null);
            this.binding.shareButton.setEnabled(false);
            this.binding.shareButton.setText(R.string.please_wait);
            mHandler.removeCallbacks(mTickExecutor);
            mHandler.postDelayed(() -> stopRecording(true), 500);
        }
    }
}

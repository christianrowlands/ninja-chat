package eu.siacs.conversations.services;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.util.MainThreadExecutor;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.Media;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallIntegration extends Connection {

    private final AppRTCAudioManager appRTCAudioManager;
    private AudioDevice initialAudioDevice = null;
    private final AtomicBoolean initialAudioDeviceConfigured = new AtomicBoolean(false);

    private List<CallEndpoint> availableEndpoints = Collections.emptyList();

    private Callback callback = null;

    public CallIntegration(final Context context) {
        if (selfManaged()) {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            this.appRTCAudioManager = null;
        } else {
            this.appRTCAudioManager = new AppRTCAudioManager(context);
            this.appRTCAudioManager.start(this::onAudioDeviceChanged);
            // TODO WebRTCWrapper would issue one call to  eventCallback.onAudioDeviceChanged
        }
        setRingbackRequested(true);
    }

    public void setCallback(final Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onShowIncomingCallUi() {
        Log.d(Config.LOGTAG, "onShowIncomingCallUi");
        this.callback.onCallIntegrationShowIncomingCallUi();
    }

    @Override
    public void onAnswer() {
        Log.d(Config.LOGTAG, "onAnswer()");
    }

    @Override
    public void onDisconnect() {
        Log.d(Config.LOGTAG, "onDisconnect()");
        this.callback.onCallIntegrationDisconnect();
    }

    @Override
    public void onReject() {
        Log.d(Config.LOGTAG, "onReject()");
    }

    @Override
    public void onReject(final String replyMessage) {
        Log.d(Config.LOGTAG, "onReject(" + replyMessage + ")");
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    public void onAvailableCallEndpointsChanged(@NonNull List<CallEndpoint> availableEndpoints) {
        Log.d(Config.LOGTAG, "onAvailableCallEndpointsChanged(" + availableEndpoints + ")");
        this.availableEndpoints = availableEndpoints;
        this.onAudioDeviceChanged(
                getAudioDeviceUpsideDownCake(getCurrentCallEndpoint()),
                ImmutableSet.copyOf(
                        Lists.transform(
                                availableEndpoints,
                                CallIntegration::getAudioDeviceUpsideDownCake)));
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    public void onCallEndpointChanged(@NonNull final CallEndpoint callEndpoint) {
        Log.d(Config.LOGTAG, "onCallEndpointChanged()");
        this.onAudioDeviceChanged(
                getAudioDeviceUpsideDownCake(callEndpoint),
                ImmutableSet.copyOf(
                        Lists.transform(
                                this.availableEndpoints,
                                CallIntegration::getAudioDeviceUpsideDownCake)));
    }

    @Override
    public void onCallAudioStateChanged(final CallAudioState state) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(Config.LOGTAG, "ignoring onCallAudioStateChange() on Upside Down Cake");
            return;
        }
        Log.d(Config.LOGTAG, "onCallAudioStateChange(" + state + ")");
        this.onAudioDeviceChanged(getAudioDeviceOreo(state), getAudioDevicesOreo(state));
    }

    public Set<AudioDevice> getAudioDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return getAudioDevicesUpsideDownCake();
        } else if (selfManaged()) {
            return getAudioDevicesOreo();
        } else {
            return getAudioDevicesFallback();
        }
    }

    public AudioDevice getSelectedAudioDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return getAudioDeviceUpsideDownCake();
        } else if (selfManaged()) {
            return getAudioDeviceOreo();
        } else {
            return getAudioDeviceFallback();
        }
    }

    public void setAudioDevice(final AudioDevice audioDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setAudioDeviceUpsideDownCake(audioDevice);
        } else if (selfManaged()) {
            setAudioDeviceOreo(audioDevice);
        } else {
            setAudioDeviceFallback(audioDevice);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private Set<AudioDevice> getAudioDevicesUpsideDownCake() {
        return ImmutableSet.copyOf(
                Lists.transform(
                        this.availableEndpoints, CallIntegration::getAudioDeviceUpsideDownCake));
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private AudioDevice getAudioDeviceUpsideDownCake() {
        return getAudioDeviceUpsideDownCake(getCurrentCallEndpoint());
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static AudioDevice getAudioDeviceUpsideDownCake(final CallEndpoint callEndpoint) {
        if (callEndpoint == null) {
            return AudioDevice.NONE;
        }
        final var endpointType = callEndpoint.getEndpointType();
        return switch (endpointType) {
            case CallEndpoint.TYPE_BLUETOOTH -> AudioDevice.BLUETOOTH;
            case CallEndpoint.TYPE_EARPIECE -> AudioDevice.EARPIECE;
            case CallEndpoint.TYPE_SPEAKER -> AudioDevice.SPEAKER_PHONE;
            case CallEndpoint.TYPE_WIRED_HEADSET -> AudioDevice.WIRED_HEADSET;
            case CallEndpoint.TYPE_STREAMING -> AudioDevice.STREAMING;
            case CallEndpoint.TYPE_UNKNOWN -> AudioDevice.NONE;
            default -> throw new IllegalStateException("Unknown endpoint type " + endpointType);
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void setAudioDeviceUpsideDownCake(final AudioDevice audioDevice) {
        final var callEndpointOptional =
                Iterables.tryFind(
                        this.availableEndpoints,
                        e -> getAudioDeviceUpsideDownCake(e) == audioDevice);
        if (callEndpointOptional.isPresent()) {
            final var endpoint = callEndpointOptional.get();
            requestCallEndpointChange(
                    endpoint,
                    MainThreadExecutor.getInstance(),
                    result -> Log.d(Config.LOGTAG, "switched to endpoint " + endpoint));
        } else {
            Log.w(Config.LOGTAG, "no endpoint found matching " + audioDevice);
        }
    }

    private Set<AudioDevice> getAudioDevicesOreo() {
        final var audioState = getCallAudioState();
        if (audioState == null) {
            Log.d(
                    Config.LOGTAG,
                    "no CallAudioState available. returning empty set for audio devices");
            return Collections.emptySet();
        }
        return getAudioDevicesOreo(audioState);
    }

    private static Set<AudioDevice> getAudioDevicesOreo(final CallAudioState callAudioState) {
        final ImmutableSet.Builder<AudioDevice> supportedAudioDevicesBuilder =
                new ImmutableSet.Builder<>();
        final var supportedRouteMask = callAudioState.getSupportedRouteMask();
        if ((supportedRouteMask & CallAudioState.ROUTE_BLUETOOTH)
                == CallAudioState.ROUTE_BLUETOOTH) {
            supportedAudioDevicesBuilder.add(AudioDevice.BLUETOOTH);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_EARPIECE) {
            supportedAudioDevicesBuilder.add(AudioDevice.EARPIECE);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_SPEAKER) == CallAudioState.ROUTE_SPEAKER) {
            supportedAudioDevicesBuilder.add(AudioDevice.SPEAKER_PHONE);
        }
        if ((supportedRouteMask & CallAudioState.ROUTE_WIRED_HEADSET)
                == CallAudioState.ROUTE_WIRED_HEADSET) {
            supportedAudioDevicesBuilder.add(AudioDevice.WIRED_HEADSET);
        }
        return supportedAudioDevicesBuilder.build();
    }

    private AudioDevice getAudioDeviceOreo() {
        final var audioState = getCallAudioState();
        if (audioState == null) {
            Log.d(Config.LOGTAG, "no CallAudioState available. returning NONE as audio device");
            return AudioDevice.NONE;
        }
        return getAudioDeviceOreo(audioState);
    }

    private static AudioDevice getAudioDeviceOreo(final CallAudioState audioState) {
        // technically we get a mask here; maybe we should query the mask instead
        return switch (audioState.getRoute()) {
            case CallAudioState.ROUTE_BLUETOOTH -> AudioDevice.BLUETOOTH;
            case CallAudioState.ROUTE_EARPIECE -> AudioDevice.EARPIECE;
            case CallAudioState.ROUTE_SPEAKER -> AudioDevice.SPEAKER_PHONE;
            case CallAudioState.ROUTE_WIRED_HEADSET -> AudioDevice.WIRED_HEADSET;
            default -> AudioDevice.NONE;
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setAudioDeviceOreo(final AudioDevice audioDevice) {
        switch (audioDevice) {
            case EARPIECE -> setAudioRoute(CallAudioState.ROUTE_EARPIECE);
            case BLUETOOTH -> setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
            case WIRED_HEADSET -> setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET);
            case SPEAKER_PHONE -> setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
    }

    private Set<AudioDevice> getAudioDevicesFallback() {
        return requireAppRtcAudioManager().getAudioDevices();
    }

    private AudioDevice getAudioDeviceFallback() {
        return requireAppRtcAudioManager().getSelectedAudioDevice();
    }

    private void setAudioDeviceFallback(final AudioDevice audioDevice) {
        requireAppRtcAudioManager().setDefaultAudioDevice(audioDevice);
    }

    @NonNull
    private AppRTCAudioManager requireAppRtcAudioManager() {
        if (this.appRTCAudioManager == null) {
            throw new IllegalStateException(
                    "You are trying to access the fallback audio manager on a modern device");
        }
        return this.appRTCAudioManager;
    }

    @Override
    public void onStateChanged(final int state) {
        Log.d(Config.LOGTAG, "onStateChanged(" + state + ")");
        if (state == STATE_DISCONNECTED) {
            final var audioManager = this.appRTCAudioManager;
            if (audioManager != null) {
                audioManager.stop();
            }
        }
    }

    public void success() {
        Log.d(Config.LOGTAG, "CallIntegration.success()");
        this.destroyWith(new DisconnectCause(DisconnectCause.LOCAL, null));
    }

    public void accepted() {
        Log.d(Config.LOGTAG, "CallIntegration.accepted()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            this.destroyWith(new DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE, null));
        } else {
            this.destroyWith(new DisconnectCause(DisconnectCause.CANCELED, null));
        }
    }

    public void error() {
        Log.d(Config.LOGTAG, "CallIntegration.error()");
        this.destroyWith(new DisconnectCause(DisconnectCause.ERROR, null));
    }

    public void retracted() {
        Log.d(Config.LOGTAG, "CallIntegration.retracted()");
        // an alternative cause would be LOCAL
        this.destroyWith(new DisconnectCause(DisconnectCause.CANCELED, null));
    }

    public void rejected() {
        Log.d(Config.LOGTAG, "CallIntegration.rejected()");
        this.destroyWith(new DisconnectCause(DisconnectCause.REJECTED, null));
    }

    public void busy() {
        Log.d(Config.LOGTAG, "CallIntegration.busy()");
        this.destroyWith(new DisconnectCause(DisconnectCause.BUSY, null));
    }

    private void destroyWith(final DisconnectCause disconnectCause) {
        if (this.getState() == STATE_DISCONNECTED) {
            Log.d(Config.LOGTAG, "CallIntegration has already been destroyed");
            return;
        }
        this.setDisconnected(disconnectCause);
        this.destroy();
    }

    public static Uri address(final Jid contact) {
        return Uri.parse(String.format("xmpp:%s", contact.toEscapedString()));
    }

    public void verifyDisconnected() {
        if (this.getState() == STATE_DISCONNECTED) {
            return;
        }
        throw new AssertionError("CallIntegration has not been disconnected");
    }

    private void onAudioDeviceChanged(
            final CallIntegration.AudioDevice selectedAudioDevice,
            final Set<CallIntegration.AudioDevice> availableAudioDevices) {
        if (this.initialAudioDevice != null
                && this.initialAudioDeviceConfigured.compareAndSet(false, true)) {
            if (availableAudioDevices.contains(this.initialAudioDevice)) {
                setAudioDevice(this.initialAudioDevice);
                Log.d(Config.LOGTAG, "configured initial audio device");
            } else {
                Log.d(
                        Config.LOGTAG,
                        "initial audio device not available. available devices: "
                                + availableAudioDevices);
            }
        }
        final var callback = this.callback;
        if (callback == null) {
            return;
        }
        callback.onAudioDeviceChanged(selectedAudioDevice, availableAudioDevices);
    }

    public static boolean selfManaged() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public void setInitialAudioDevice(final AudioDevice audioDevice) {
        Log.d(Config.LOGTAG, "setInitialAudioDevice(" + audioDevice + ")");
        this.initialAudioDevice = audioDevice;
        if (CallIntegration.selfManaged()) {
            // once the 'CallIntegration' gets added to the system we receive calls to update audio
            // state
            return;
        }
        final var audioManager = requireAppRtcAudioManager();
        this.onAudioDeviceChanged(
                audioManager.getSelectedAudioDevice(), audioManager.getAudioDevices());
    }

    /** AudioDevice is the names of possible audio devices that we currently support. */
    public enum AudioDevice {
        NONE,
        SPEAKER_PHONE,
        WIRED_HEADSET,
        EARPIECE,
        BLUETOOTH,
        STREAMING
    }

    public static AudioDevice initialAudioDevice(final Set<Media> media) {
        if (Media.audioOnly(media)) {
            return AudioDevice.EARPIECE;
        } else {
            return AudioDevice.SPEAKER_PHONE;
        }
    }

    public interface Callback {
        void onCallIntegrationShowIncomingCallUi();

        void onCallIntegrationDisconnect();

        void onAudioDeviceChanged(
                CallIntegration.AudioDevice selectedAudioDevice,
                Set<CallIntegration.AudioDevice> availableAudioDevices);
    }
}
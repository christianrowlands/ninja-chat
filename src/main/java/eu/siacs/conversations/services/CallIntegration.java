package eu.siacs.conversations.services;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.MainThreadExecutor;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.Media;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallIntegration extends Connection {

    /**
     * OnePlus 6 (Android 8.1-11) Device is buggy and always starts the OS call screen even though
     * we want to be self managed
     *
     * <p>Samsung Galaxy Tab A claims to have FEATURE_CONNECTION_SERVICE but then throws
     * SecurityException when invoking placeCall(). Both Stock and LineageOS have this problem.
     *
     * <p>Lenovo Yoga Smart Tab YT-X705F claims to have FEATURE_CONNECTION_SERVICE but throws
     * SecurityException
     */
    private static final List<String> BROKEN_DEVICE_MODELS =
            Arrays.asList("OnePlus6", "gtaxlwifi", "YT-X705F");

    public static final int DEFAULT_TONE_VOLUME = 60;
    private static final int DEFAULT_MEDIA_PLAYER_VOLUME = 90;

    private final Context context;

    private final AppRTCAudioManager appRTCAudioManager;
    private AudioDevice initialAudioDevice = null;

    private boolean isAudioRoutingRequested = false;
    private final AtomicBoolean initialAudioDeviceConfigured = new AtomicBoolean(false);
    private final AtomicBoolean delayedDestructionInitiated = new AtomicBoolean(false);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

    private List<CallEndpoint> availableEndpoints = Collections.emptyList();
    private boolean isMicrophoneEnabled = true;

    private Callback callback = null;

    public CallIntegration(final Context context) {
        this.context = context.getApplicationContext();
        if (selfManaged()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            } else {
                throw new AssertionError(
                        "Trying to set connection properties on unsupported version");
            }
            this.appRTCAudioManager = null;
        } else {
            this.appRTCAudioManager = new AppRTCAudioManager(context);
            this.appRTCAudioManager.setAudioManagerEvents(this::onAudioDeviceChanged);
        }
        setRingbackRequested(true);
        setConnectionCapabilities(CAPABILITY_MUTE | CAPABILITY_RESPOND_VIA_TEXT);
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
        this.callback.onCallIntegrationAnswer();
    }

    @Override
    public void onDisconnect() {
        Log.d(Config.LOGTAG, "onDisconnect()");
        this.callback.onCallIntegrationDisconnect();
    }

    @Override
    public void onReject() {
        this.callback.onCallIntegrationReject();
    }

    @Override
    public void onReject(final String replyMessage) {
        Log.d(Config.LOGTAG, "onReject(" + replyMessage + ")");
        this.callback.onCallIntegrationReject();
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
        if (selfManaged() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(Config.LOGTAG, "ignoring onCallAudioStateChange() on Upside Down Cake");
            return;
        }
        setMicrophoneEnabled(!state.isMuted());
        Log.d(Config.LOGTAG, "onCallAudioStateChange(" + state + ")");
        this.onAudioDeviceChanged(getAudioDeviceOreo(state), getAudioDevicesOreo(state));
    }

    @Override
    public void onMuteStateChanged(final boolean isMuted) {
        Log.d(Config.LOGTAG, "onMuteStateChanged(" + isMuted + ")");
        setMicrophoneEnabled(!isMuted);
    }

    private void setMicrophoneEnabled(final boolean enabled) {
        this.isMicrophoneEnabled = enabled;
        this.callback.onCallIntegrationMicrophoneEnabled(enabled);
    }

    public boolean isMicrophoneEnabled() {
        return this.isMicrophoneEnabled;
    }

    public Set<AudioDevice> getAudioDevices() {
        if (notSelfManaged(context)) {
            return getAudioDevicesFallback();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return getAudioDevicesUpsideDownCake();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getAudioDevicesOreo();
        } else {
            throw new AssertionError("Trying to get audio devices on unsupported version");
        }
    }

    public AudioDevice getSelectedAudioDevice() {
        if (notSelfManaged(context)) {
            return getAudioDeviceFallback();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return getAudioDeviceUpsideDownCake();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getAudioDeviceOreo();
        } else {
            throw new AssertionError("Trying to get selected audio device on unsupported version");
        }
    }

    public void setAudioDevice(final AudioDevice audioDevice) {
        if (notSelfManaged(context)) {
            setAudioDeviceFallback(audioDevice);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setAudioDeviceUpsideDownCake(audioDevice);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setAudioDeviceOreo(audioDevice);
        } else {
            throw new AssertionError("Trying to set audio devices on unsupported version");
        }
    }

    public void setAudioDeviceWhenAvailable(final AudioDevice audioDevice) {
        final var available = getAudioDevices();
        if (available.contains(audioDevice) && !available.contains(AudioDevice.BLUETOOTH)) {
            this.setAudioDevice(audioDevice);
        } else {
            Log.d(
                    Config.LOGTAG,
                    "application requested to switch to "
                            + audioDevice
                            + " but we won't because available devices are "
                            + available);
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
        final var audioDevice = requireAppRtcAudioManager().getSelectedAudioDevice();
        return audioDevice == null ? AudioDevice.NONE : audioDevice;
    }

    private void setAudioDeviceFallback(final AudioDevice audioDevice) {
        final var audioManager = requireAppRtcAudioManager();
        audioManager.executeOnMain(() -> audioManager.setDefaultAudioDevice(audioDevice));
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
    public void onSilence() {
        this.callback.onCallIntegrationSilence();
    }

    @Override
    public void onStateChanged(final int state) {
        Log.d(Config.LOGTAG, "onStateChanged(" + state + ")");
        if (notSelfManaged(context)) {
            if (state == STATE_DIALING) {
                requireAppRtcAudioManager().startRingBack();
            } else {
                requireAppRtcAudioManager().stopRingBack();
            }
        }
        if (state == STATE_ACTIVE) {
            playConnectedSound();
        } else if (state == STATE_DISCONNECTED) {
            final var audioManager = this.appRTCAudioManager;
            if (audioManager != null) {
                audioManager.executeOnMain(audioManager::stop);
            }
        }
    }

    private void playConnectedSound() {
        final var audioAttributes =
                new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build();
        final var mediaPlayer =
                MediaPlayer.create(
                        context,
                        R.raw.connected,
                        audioAttributes,
                        AudioManager.AUDIO_SESSION_ID_GENERATE);
        mediaPlayer.setVolume(
                DEFAULT_MEDIA_PLAYER_VOLUME / 100f, DEFAULT_MEDIA_PLAYER_VOLUME / 100f);
        mediaPlayer.start();
    }

    public void success() {
        Log.d(Config.LOGTAG, "CallIntegration.success()");
        startTone(DEFAULT_TONE_VOLUME, ToneGenerator.TONE_CDMA_CALLDROP_LITE, 375);
        this.destroyWithDelay(new DisconnectCause(DisconnectCause.LOCAL, null), 375);
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
        startTone(DEFAULT_TONE_VOLUME, ToneGenerator.TONE_CDMA_CALLDROP_LITE, 375);
        this.destroyWithDelay(new DisconnectCause(DisconnectCause.ERROR, null), 375);
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
        startTone(80, ToneGenerator.TONE_CDMA_NETWORK_BUSY, 2500);
        this.destroyWithDelay(new DisconnectCause(DisconnectCause.BUSY, null), 2500);
    }

    private void destroyWithDelay(final DisconnectCause disconnectCause, final int delay) {
        if (this.delayedDestructionInitiated.compareAndSet(false, true)) {
            JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(
                    () -> {
                        this.setDisconnected(disconnectCause);
                        this.destroyCallIntegration();
                    },
                    delay,
                    TimeUnit.MILLISECONDS);
        } else {
            Log.w(Config.LOGTAG, "CallIntegration destruction has already been scheduled!");
        }
    }

    private void destroyWith(final DisconnectCause disconnectCause) {
        if (this.getState() == STATE_DISCONNECTED || this.delayedDestructionInitiated.get()) {
            Log.d(Config.LOGTAG, "CallIntegration has already been destroyed");
            return;
        }
        this.setDisconnected(disconnectCause);
        this.destroyCallIntegration();
        Log.d(Config.LOGTAG, "destroyed!");
    }

    private void startTone(final int volume, final int toneType, final int durationMs) {
        final ToneGenerator toneGenerator;
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, volume);
        } catch (final RuntimeException e) {
            Log.e(Config.LOGTAG, "could not initialize tone generator", e);
            return;
        }
        toneGenerator.startTone(toneType, durationMs);
    }

    public static Uri address(final Jid contact) {
        return Uri.parse(String.format("xmpp:%s", contact.toEscapedString()));
    }

    public void verifyDisconnected() {
        if (this.getState() == STATE_DISCONNECTED || this.delayedDestructionInitiated.get()) {
            return;
        }
        throw new AssertionError("CallIntegration has not been disconnected");
    }

    private void onAudioDeviceChanged(
            final CallIntegration.AudioDevice selectedAudioDevice,
            final Set<CallIntegration.AudioDevice> availableAudioDevices) {
        if (isAudioRoutingRequested) {
            configureInitialAudioDevice(availableAudioDevices);
        }
        final var callback = this.callback;
        if (callback == null) {
            return;
        }
        callback.onAudioDeviceChanged(selectedAudioDevice, availableAudioDevices);
    }

    private void configureInitialAudioDevice(final Set<AudioDevice> availableAudioDevices) {
        final var initialAudioDevice = this.initialAudioDevice;
        if (initialAudioDevice == null) {
            Log.d(Config.LOGTAG, "skipping configureInitialAudioDevice()");
            return;
        }
        final var target = this.initialAudioDevice;
        if (this.initialAudioDeviceConfigured.compareAndSet(false, true)) {
            if (availableAudioDevices.contains(target)
                    && !availableAudioDevices.contains(AudioDevice.BLUETOOTH)) {
                setAudioDevice(target);
                Log.d(Config.LOGTAG, "configured initial audio device: " + target);
            } else {
                Log.d(
                        Config.LOGTAG,
                        "not setting initial audio device. available devices: "
                                + availableAudioDevices);
            }
        }
    }

    private boolean selfManaged() {
        return selfManaged(context);
    }

    public static boolean selfManaged(final Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && hasSystemFeature(context)
                && isDeviceModelSupported();
    }

    public static boolean hasSystemFeature(final Context context) {
        final var packageManager = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_TELECOM);
        } else {
            //noinspection deprecation
            return packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE);
        }
    }

    private static boolean isDeviceModelSupported() {
        if (BROKEN_DEVICE_MODELS.contains(Build.DEVICE)) {
            return false;
        }
        // all Realme devices at least up to and including Android 11 are broken
        if ("realme".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return false;
        }
        // we are relatively sure that old Oppo devices are broken too. We get reports of 'number
        // not sent' from Oppo R15x (Android 10)
        if ("OPPO".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return false;
        }
        // we only know of one Umidigi device (BISON_GT2_5G) that doesn't work (audio is not being
        // routed properly) However with those devices being extremely rare it's impossible to gauge
        // how many might be effected and no Naomi Wu around to clarify with the company directly
        if ("umidigi".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            return false;
        }
        return true;
    }

    public static boolean notSelfManaged(final Context context) {
        return !selfManaged(context);
    }

    public void setInitialAudioDevice(final AudioDevice audioDevice) {
        Log.d(Config.LOGTAG, "setInitialAudioDevice(" + audioDevice + ")");
        this.initialAudioDevice = audioDevice;
    }

    public void startAudioRouting() {
        this.isAudioRoutingRequested = true;
        if (selfManaged()) {
            final var devices = getAudioDevices();
            if (devices.isEmpty()) {
                return;
            }
            configureInitialAudioDevice(devices);
            return;
        }
        final var audioManager = requireAppRtcAudioManager();
        audioManager.executeOnMain(
                () -> {
                    audioManager.start();
                    this.onAudioDeviceChanged(
                            audioManager.getSelectedAudioDevice(), audioManager.getAudioDevices());
                });
    }

    private void destroyCallIntegration() {
        super.destroy();
        this.isDestroyed.set(true);
    }

    public boolean isDestroyed() {
        return this.isDestroyed.get();
    }

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

        void onCallIntegrationReject();

        void onCallIntegrationAnswer();

        void onCallIntegrationSilence();

        void onCallIntegrationMicrophoneEnabled(boolean enabled);
    }
}

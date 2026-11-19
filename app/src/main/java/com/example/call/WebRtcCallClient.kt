package com.example.call

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallClient(
    private val context: Context,
    private val isVideoCall: Boolean,
    private val onIceCandidateGenerated: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit
) {
    val eglBase: EglBase = EglBase.create()

    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var screenCapturer: VideoCapturer? = null
    private var screenVideoSource: VideoSource? = null
    private var screenTextureHelper: SurfaceTextureHelper? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSender: RtpSender? = null
    private var audioSender: RtpSender? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _iceState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceState: StateFlow<PeerConnection.IceConnectionState> = _iceState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(isVideoCall)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(isVideoCall)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isNoiseCancellationEnabled = MutableStateFlow(true)
    val isNoiseCancellationEnabled: StateFlow<Boolean> = _isNoiseCancellationEnabled.asStateFlow()

    private val _cameraUnavailable = MutableStateFlow(false)
    val cameraUnavailable: StateFlow<Boolean> = _cameraUnavailable.asStateFlow()

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false
    private var isDisposed = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val adm = runCatching {
            JavaAudioDeviceModule.builder(context.applicationContext)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .createAudioDeviceModule()
        }.getOrNull()
        audioDeviceModule = adm

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val factoryBuilder = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
        if (adm != null) {
            factoryBuilder.setAudioDeviceModule(adm)
        }
        factory = factoryBuilder.createPeerConnectionFactory()

        configureAudioRouting(speakerOn = isVideoCall)
        setupPeerConnection()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                state ?: return
                _iceState.value = state
                onConnectionStateChanged(state)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                onIceCandidateGenerated(
                    candidate.sdpMid ?: "0",
                    candidate.sdpMLineIndex,
                    candidate.sdp
                )
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                val track = stream?.videoTracks?.firstOrNull()
                if (track != null) _remoteVideoTrack.value = track
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }
        })

        startLocalMedia()
    }

    private fun buildAudioConstraints(ancEnabled: Boolean): MediaConstraints {
        val flag = if (ancEnabled) "true" else "false"
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
    }

    private fun startLocalMedia() {
        try {
            audioSource = factory.createAudioSource(buildAudioConstraints(_isNoiseCancellationEnabled.value))
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)?.apply {
                setEnabled(!_isMuted.value)
            }
            localAudioTrack?.let { track ->
                audioSender = peerConnection?.addTrack(track, listOf("ARDAMS"))
                applySenderBitrate(audioSender, maxBitrateBps = 64_000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio capture setup failed: ${e.message}")
        }

        if (isVideoCall) {
            startLocalCamera()
        }
    }

    private fun applySenderBitrate(sender: RtpSender?, maxBitrateBps: Int) {
        if (sender == null) return
        runCatching {
            val params = sender.parameters
            params.encodings?.forEach { encoding ->
                encoding.maxBitrateBps = maxBitrateBps
            }
            sender.parameters = params
        }
    }

    private fun startLocalCamera() {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            val chosenDevice = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull()
            if (chosenDevice == null) {
                _cameraUnavailable.value = true
                return
            }
            val capturer = enumerator.createCapturer(chosenDevice, null)
            if (capturer == null) {
                _cameraUnavailable.value = true
                return
            }
            val supportedFormats = runCatching { enumerator.getSupportedFormats(chosenDevice) }.getOrNull().orEmpty()
            val bestFormat = supportedFormats
                .filter { it.width <= 1280 && it.height <= 720 }
                .maxByOrNull { it.width * it.height }
            val captureW = bestFormat?.width ?: 1280
            val captureH = bestFormat?.height ?: 720

            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context.applicationContext, videoSource!!.capturerObserver)
            runCatching {
                capturer.startCapture(captureW, captureH, 30)
            }.onFailure {
                capturer.startCapture(640, 480, 30)
            }

            val track = factory.createVideoTrack("ARDAMSv0", videoSource).apply {
                setEnabled(_isCameraEnabled.value)
            }
            _localVideoTrack.value = track
            if (videoSender != null) {
                videoSender?.setTrack(track, false)
            } else {
                videoSender = peerConnection?.addTrack(track, listOf("ARDAMS"))
            }
            applySenderBitrate(videoSender, maxBitrateBps = 2_500_000)
        } catch (e: Exception) {
            Log.w(TAG, "Camera initialization failed: ${e.message}")
            _cameraUnavailable.value = true
        }
    }

    fun startScreenSharing(mediaProjectionPermissionResultData: Intent): Result<Unit> {
        if (isDisposed) return Result.failure(IllegalStateException("Call disposed"))
        return runCatching {
            ScreenShareForegroundService.start(context)
            stopCameraCapturerOnly()

            val capturer = ScreenCapturerAndroid(
                mediaProjectionPermissionResultData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopScreenSharing()
                    }
                }
            )
            val texHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
            val src = factory.createVideoSource(true)
            capturer.initialize(texHelper, context.applicationContext, src.capturerObserver)
            capturer.startCapture(720, 1280, 15)

            screenCapturer = capturer
            screenTextureHelper = texHelper
            screenVideoSource = src

            val screenTrack = factory.createVideoTrack("ARDAMSv_screen", src).apply {
                setEnabled(true)
            }
            _localVideoTrack.value = screenTrack
            if (videoSender != null) {
                videoSender?.setTrack(screenTrack, false)
            } else {
                videoSender = peerConnection?.addTrack(screenTrack, listOf("ARDAMS"))
            }
            _isScreenSharing.value = true
        }
    }

    fun stopScreenSharing() {
        if (!_isScreenSharing.value && screenCapturer == null) return
        _isScreenSharing.value = false
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        screenCapturer = null
        runCatching { screenTextureHelper?.dispose() }
        screenTextureHelper = null
        runCatching { screenVideoSource?.dispose() }
        screenVideoSource = null
        ScreenShareForegroundService.stop(context)

        if (!isDisposed) {
            if (isVideoCall) {
                startLocalCamera()
            } else {
                _localVideoTrack.value = null
                runCatching { videoSender?.setTrack(null, false) }
            }
        }
    }

    private fun stopCameraCapturerOnly() {
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { videoSource?.dispose() }
        videoSource = null
    }

    fun createOffer(onOfferCreated: (String) -> Unit, onError: (String) -> Unit) {
        val pc = peerConnection ?: return onError("PeerConnection unavailable")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    onError("Failed to create SDP offer")
                    return
                }
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        onOfferCreated(sdp.description)
                    }
                    override fun onSetFailure(msg: String?) {
                        onError(msg ?: "Failed to set local offer SDP")
                    }
                }, sdp)
            }
            override fun onCreateFailure(msg: String?) {
                onError(msg ?: "Failed to create SDP offer")
            }
        }, constraints)
    }

    fun handleRemoteOfferAndCreateAnswer(
        offerSdp: String,
        onAnswerCreated: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = peerConnection ?: return onError("PeerConnection unavailable")
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainPendingIceCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer == null) {
                            onError("Failed to create SDP answer")
                            return
                        }
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                onAnswerCreated(answer.description)
                            }
                            override fun onSetFailure(msg: String?) {
                                onError(msg ?: "Failed to set local answer SDP")
                            }
                        }, answer)
                    }
                    override fun onCreateFailure(msg: String?) {
                        onError(msg ?: "Failed to create SDP answer")
                    }
                }, constraints)
            }
            override fun onSetFailure(msg: String?) {
                onError(msg ?: "Failed to set remote offer SDP")
            }
        }, remoteSdp)
    }

    fun setRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        if (isRemoteDescriptionSet) return
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainPendingIceCandidates()
            }
            override fun onSetFailure(msg: String?) {
                Log.w(TAG, "setRemoteAnswer failed: $msg")
            }
        }, remoteSdp)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (!isRemoteDescriptionSet) {
            pendingRemoteCandidates.add(ice)
        } else {
            peerConnection?.addIceCandidate(ice)
        }
    }

    private fun drainPendingIceCandidates() {
        val pc = peerConnection ?: return
        pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        localAudioTrack?.setEnabled(!next)
    }

    fun toggleCamera() {
        if (!isVideoCall) return
        val next = !_isCameraEnabled.value
        _isCameraEnabled.value = next
        _localVideoTrack.value?.setEnabled(next)
    }

    fun switchCamera() {
        if (_isScreenSharing.value) return
        videoCapturer?.switchCamera(null)
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        configureAudioRouting(next)
    }

    fun toggleNoiseCancellation() {
        if (isDisposed) return
        val next = !_isNoiseCancellationEnabled.value
        _isNoiseCancellationEnabled.value = next
        runCatching {
            val prevTrack = localAudioTrack
            val prevSource = audioSource
            val newSource = factory.createAudioSource(buildAudioConstraints(next))
            val newTrack = factory.createAudioTrack("ARDAMSa0_nc", newSource)?.apply {
                setEnabled(!_isMuted.value)
            }
            if (newTrack != null) {
                audioSender?.setTrack(newTrack, false)
                localAudioTrack = newTrack
                audioSource = newSource
                applySenderBitrate(audioSender, maxBitrateBps = 64_000)
                prevTrack?.setEnabled(false)
                prevSource?.dispose()
            } else {
                newSource.dispose()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureAudioRouting(speakerOn: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (speakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.isSpeakerphoneOn = speakerOn
                }
            } else {
                audioManager.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing update failed: ${e.message}")
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        stopScreenSharing()
        runCatching {
            stopCameraCapturerOnly()
            audioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            factory.dispose()
            audioDeviceModule?.release()
            audioDeviceModule = null
            eglBase.release()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(msg: String?) {}
        override fun onSetFailure(msg: String?) {}
    }

    private companion object {
        const val TAG = "WebRtcCallClient"
    }
}

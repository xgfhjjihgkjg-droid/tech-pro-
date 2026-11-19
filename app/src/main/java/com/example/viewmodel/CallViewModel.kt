package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.call.WebRtcCallClient
import com.example.crypto.CryptoEngine
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ChatRepository
import com.example.data.DirectoryUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import java.util.UUID

class CallViewModel(
    private val appContext: Context,
    private val repository: ChatRepository,
    private val cryptoEngine: CryptoEngine,
    private val currentUserId: String,
    private val peerId: String,
    private val callType: CallType,
    private val incomingCallId: String?
) : ViewModel() {

    private var rtcClient: WebRtcCallClient? = null
    private var activeCallId: String = incomingCallId ?: "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    private var participantIds: List<String> = listOf(currentUserId, peerId)
    private val processedCandidateIds = mutableSetOf<String>()
    private var answerApplied = false
    private var timerJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var sessionObserverJob: Job? = null
    private var candidateObserverJob: Job? = null
    private var hasStartedSignaling = false

    val peerIdValue: String get() = peerId
    val callTypeValue: CallType get() = callType

    private val _callSession = MutableStateFlow<CallSession?>(null)
    val callSession: StateFlow<CallSession?> = _callSession.asStateFlow()

    private val _peerUser = MutableStateFlow<DirectoryUser?>(null)
    val peerUser: StateFlow<DirectoryUser?> = _peerUser.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(callType == CallType.VIDEO)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(callType == CallType.VIDEO)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isNoiseCancellationEnabled = MutableStateFlow(true)
    val isNoiseCancellationEnabled: StateFlow<Boolean> = _isNoiseCancellationEnabled.asStateFlow()

    private val _cameraUnavailable = MutableStateFlow(false)
    val cameraUnavailable: StateFlow<Boolean> = _cameraUnavailable.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    val callId: String
        get() = activeCallId

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _ringingSecondsLeft = MutableStateFlow<Int?>(null)
    val ringingSecondsLeft: StateFlow<Int?> = _ringingSecondsLeft.asStateFlow()

    private val _callError = MutableStateFlow<String?>(null)
    val callError: StateFlow<String?> = _callError.asStateFlow()

    private val _callEnded = MutableStateFlow(false)
    val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()

    val eglBaseContext: EglBase.Context?
        get() = rtcClient?.eglBase?.eglBaseContext

    init {
        viewModelScope.launch {
            repository.observeDirectoryUser(peerId)
                .catch { error -> unwrapFirestoreOrThrow(error, "peerProfile") }
                .collect { _peerUser.value = it }
        }
    }

    fun startCallMediaAndSignaling(myProfile: DirectoryUser, peerProfile: DirectoryUser) {
        if (hasStartedSignaling) return
        hasStartedSignaling = true

        val client = WebRtcCallClient(
            context = appContext,
            isVideoCall = callType == CallType.VIDEO,
            onIceCandidateGenerated = { sdpMid, sdpMLineIndex, candidate ->
                viewModelScope.launch {
                    repository.addIceCandidate(
                        callId = activeCallId,
                        participantIds = participantIds,
                        sdpMid = sdpMid,
                        sdpMLineIndex = sdpMLineIndex,
                        candidate = candidate
                    )
                }
            },
            onConnectionStateChanged = { iceState ->
                if (iceState == PeerConnection.IceConnectionState.CONNECTED ||
                    iceState == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    stopRingTimeout()
                    startCallTimer()
                    viewModelScope.launch {
                        val current = _callSession.value?.statusEnum
                        if (current == CallStatus.ACCEPTED || current == CallStatus.RINGING) {
                            repository.updateCallStatus(activeCallId, CallStatus.CONNECTED)
                        }
                    }
                }
            }
        )
        rtcClient = client

        viewModelScope.launch { client.localVideoTrack.collect { _localVideoTrack.value = it } }
        viewModelScope.launch { client.remoteVideoTrack.collect { _remoteVideoTrack.value = it } }
        viewModelScope.launch { client.isMuted.collect { _isMuted.value = it } }
        viewModelScope.launch { client.isCameraEnabled.collect { _isCameraEnabled.value = it } }
        viewModelScope.launch { client.isSpeakerOn.collect { _isSpeakerOn.value = it } }
        viewModelScope.launch { client.isNoiseCancellationEnabled.collect { _isNoiseCancellationEnabled.value = it } }
        viewModelScope.launch { client.cameraUnavailable.collect { _cameraUnavailable.value = it } }
        viewModelScope.launch { client.isScreenSharing.collect { _isScreenSharing.value = it } }

        if (incomingCallId == null) {
            // Outgoing call: create Offer SDP and write /calls/{callId}
            participantIds = listOf(myProfile.uid, peerProfile.uid)
            val verificationCode = cryptoEngine.computeCallVerificationCode(
                myProfile.publicKey,
                peerProfile.publicKey,
                activeCallId
            )
            client.createOffer(
                onOfferCreated = { offerSdp ->
                    viewModelScope.launch {
                        repository.createCallSession(
                            callId = activeCallId,
                            caller = myProfile,
                            callee = peerProfile,
                            callType = callType,
                            offerSdp = offerSdp,
                            e2eeFingerprint = verificationCode
                        ).onSuccess {
                            observeCallAndCandidates()
                            startRingTimeout()
                        }.onFailure { err ->
                            _callError.value = err.message ?: "Failed to start call signaling"
                        }
                    }
                },
                onError = { err ->
                    _callError.value = err
                }
            )
        } else {
            // Incoming call: observe call doc, apply Offer SDP, and send Answer SDP
            observeCallAndCandidates()
        }
    }

    private fun startRingTimeout() {
        if (ringTimeoutJob != null || incomingCallId != null) return
        ringTimeoutJob = viewModelScope.launch {
            for (remaining in RING_TIMEOUT_SECONDS downTo 1) {
                if (_callEnded.value || answerApplied) {
                    _ringingSecondsLeft.value = null
                    return@launch
                }
                _ringingSecondsLeft.value = remaining
                delay(1000L)
            }
            _ringingSecondsLeft.value = 0
            val current = _callSession.value
            if (!_callEnded.value && !answerApplied &&
                (current == null || current.statusEnum == CallStatus.RINGING)
            ) {
                _callError.value = "No answer after ${RING_TIMEOUT_SECONDS}s"
                repository.updateCallStatus(activeCallId, CallStatus.MISSED)
                _callEnded.value = true
            }
        }
    }

    private fun stopRingTimeout() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        _ringingSecondsLeft.value = null
    }

    private fun observeCallAndCandidates() {
        sessionObserverJob?.cancel()
        sessionObserverJob = viewModelScope.launch {
            repository.observeCallSession(activeCallId)
                .catch { error ->
                    val root = unwrapFirestoreOrThrow(error, "observeCallSession")
                    _callError.value = root.message ?: "Call stream interrupted"
                }
                .collect { session ->
                    if (session == null) return@collect
                    _callSession.value = session
                    participantIds = session.participantIds

                    if (session.statusEnum.isTerminal) {
                        stopRingTimeout()
                        _callEnded.value = true
                        return@collect
                    }

                    if (incomingCallId != null && !answerApplied && session.callerId != currentUserId) {
                        answerApplied = true
                        stopRingTimeout()
                        rtcClient?.handleRemoteOfferAndCreateAnswer(
                            offerSdp = session.offerSdp,
                            onAnswerCreated = { answerSdp ->
                                viewModelScope.launch {
                                    repository.acceptCallWithAnswer(activeCallId, answerSdp)
                                    startCallTimer()
                                }
                            },
                            onError = { err -> _callError.value = err }
                        )
                    } else if (incomingCallId == null && !answerApplied && !session.answerSdp.isNullOrBlank()) {
                        answerApplied = true
                        stopRingTimeout()
                        rtcClient?.setRemoteAnswer(session.answerSdp)
                        startCallTimer()
                    }
                }
        }

        candidateObserverJob?.cancel()
        candidateObserverJob = viewModelScope.launch {
            repository.observeIceCandidates(activeCallId, currentUserId)
                .catch { error -> unwrapFirestoreOrThrow(error, "observeIceCandidates") }
                .collect { candidates ->
                    candidates.forEach { cand ->
                        if (cand.senderId != currentUserId && cand.id !in processedCandidateIds) {
                            processedCandidateIds.add(cand.id)
                            rtcClient?.addRemoteIceCandidate(
                                sdpMid = cand.sdpMid,
                                sdpMLineIndex = cand.sdpMLineIndex.toInt(),
                                candidate = cand.candidate
                            )
                        }
                    }
                }
        }
    }

    private fun startCallTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _callDurationSeconds.value += 1L
            }
        }
    }

    fun toggleMute() = rtcClient?.toggleMute()
    fun toggleCamera() = rtcClient?.toggleCamera()
    fun switchCamera() = rtcClient?.switchCamera()
    fun toggleSpeaker() = rtcClient?.toggleSpeaker()
    fun toggleNoiseCancellation() = rtcClient?.toggleNoiseCancellation()

    fun startScreenSharing(permissionResultData: Intent): Result<Unit> =
        rtcClient?.startScreenSharing(permissionResultData)
            ?: Result.failure(IllegalStateException("Call signaling not ready yet"))

    fun stopScreenSharing() = rtcClient?.stopScreenSharing()

    fun endCall() {
        stopRingTimeout()
        viewModelScope.launch {
            val current = _callSession.value
            if (current != null && !current.statusEnum.isTerminal) {
                val targetStatus = if (current.statusEnum == CallStatus.RINGING && current.callerId != currentUserId) {
                    CallStatus.DECLINED
                } else {
                    CallStatus.ENDED
                }
                repository.updateCallStatus(activeCallId, targetStatus)
            }
            _callEnded.value = true
        }
    }

    fun releaseCallResources() {
        timerJob?.cancel()
        timerJob = null
        stopRingTimeout()
        sessionObserverJob?.cancel()
        sessionObserverJob = null
        candidateObserverJob?.cancel()
        candidateObserverJob = null
        rtcClient?.dispose()
        rtcClient = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseCallResources()
    }

    private fun unwrapFirestoreOrThrow(error: Throwable, opName: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error in $opName", root)
        return root
    }

    private companion object {
        const val TAG = "CallVM"
        const val RING_TIMEOUT_SECONDS = 30
    }
}

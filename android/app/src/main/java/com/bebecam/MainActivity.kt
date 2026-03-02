package com.bebecam

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.PictureInPictureParams
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceViewRenderer
    private lateinit var progressBar: ProgressBar

    // UI Elements
    private lateinit var interactionLayer: View
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var settingsPanel: View
    private lateinit var nightOverlay: View
    private lateinit var brightnessOverlay: View
    private lateinit var monitoringGlow: View
    private lateinit var liveIndicator: LinearLayout
    private lateinit var liveDot: View
    private lateinit var liveText: TextView
    private lateinit var babyIndicator: LinearLayout
    private lateinit var babyText: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnNightMode: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var sliderBrightness: SeekBar
    private lateinit var valBrightness: TextView
    private var isNightMode = false
    private var isSettingsOpen = false

    // UI Auto-hide
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideUIRunnable = Runnable { hideUI() }
    private var isUIHidden = false

    private val apSsid = "BebeCam"
    private val apPass = "bebecam_auto"
    private val whepUrl = "http://192.168.4.1:8080/bebe/whep"

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // WebRTC Components
    private lateinit var eglBaseContext: EglBase.Context
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    // AI Components
    private lateinit var faceDetector: FaceDetector
    private var isAnalyzing = false
    private var lastAnalysisTime = 0L
    private var sleepingFramesCount = 0
    private val requiredSleepingFrames = 10

    // OkHttp Client
    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind all views
        surfaceView = findViewById(R.id.surfaceView)
        progressBar = findViewById(R.id.progressBar)
        interactionLayer = findViewById(R.id.interactionLayer)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        settingsPanel = findViewById(R.id.settingsPanel)
        nightOverlay = findViewById(R.id.nightOverlay)
        brightnessOverlay = findViewById(R.id.brightnessOverlay)
        monitoringGlow = findViewById(R.id.monitoringGlow)
        liveIndicator = findViewById(R.id.liveIndicator)
        liveDot = findViewById(R.id.liveDot)
        liveText = findViewById(R.id.liveText)
        babyIndicator = findViewById(R.id.babyIndicator)
        babyText = findViewById(R.id.babyText)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnNightMode = findViewById(R.id.btnNightMode)
        btnSettings = findViewById(R.id.btnSettings)
        sliderBrightness = findViewById(R.id.sliderBrightness)
        valBrightness = findViewById(R.id.valBrightness)

        setupUI()
        initWebRTC()
        initAI()

        connectivityManager = getSystemService<ConnectivityManager>()!!
        requestWifiNetwork()
    }

    private fun setupUI() {
        interactionLayer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isSettingsOpen) {
                toggleSettingsPanel(false)
                return@setOnClickListener
            }
            if (isUIHidden) wakeUI() else hideUI()
        }

        btnRefresh.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            setUiStateConnecting()
            peerConnection?.close()
            startWebRTCStream()
            wakeUI()
        }

        btnNightMode.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isNightMode = !isNightMode
            nightOverlay.visibility = if (isNightMode) View.VISIBLE else View.GONE
            btnNightMode.alpha = if (isNightMode) 0.5f else 1.0f
            wakeUI()
        }

        btnSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleSettingsPanel(!isSettingsOpen)
        }

        setupSliders()

        topBar.setOnClickListener { wakeUI() }
        bottomBar.setOnClickListener { wakeUI() }
        settingsPanel.setOnClickListener { wakeUI() }

        wakeUI()
    }

    private fun setupSliders() {
        // Init values
        updateVideoEffects()

        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            private var lastSteppedProgress = -1
            
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val stepped = (progress / 10) * 10
                if (stepped != lastSteppedProgress) {
                    lastSteppedProgress = stepped
                    
                    // Specific Haptics
                    val actualVal = stepped + 20
                    if (actualVal == 100) {
                        seekBar.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } else {
                        seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    
                    updateVideoEffects()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { wakeUI() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { wakeUI() }
        }

        sliderBrightness.setOnSeekBarChangeListener(sliderListener)
    }

    private fun updateVideoEffects() {
        val bProgress = (sliderBrightness.progress / 10) * 10 + 20
        
        valBrightness.text = getString(R.string.percentage_format, bProgress)

        // BRIGHTNESS (via dual-mode overlay)
        if (bProgress < 100) {
            brightnessOverlay.setBackgroundColor(Color.BLACK)
            brightnessOverlay.alpha = (100 - bProgress) / 100f * 0.8f
        } else if (bProgress > 100) {
            brightnessOverlay.setBackgroundColor(Color.WHITE)
            brightnessOverlay.alpha = (bProgress - 100) / 100f * 0.4f
        } else {
            brightnessOverlay.alpha = 0f
        }
    }

    private fun toggleSettingsPanel(show: Boolean) {
        isSettingsOpen = show
        if (show) {
            hideHandler.removeCallbacks(hideUIRunnable)
            settingsPanel.translationY = 50f
            settingsPanel.alpha = 0f
            settingsPanel.visibility = View.VISIBLE
            settingsPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start()
        } else {
            settingsPanel.animate()
                .translationY(50f)
                .alpha(0f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        settingsPanel.visibility = View.INVISIBLE
                    }
                })
                .start()
            wakeUI()
        }
    }

    private fun wakeUI() {
        hideHandler.removeCallbacks(hideUIRunnable)
        if (isUIHidden) {
            isUIHidden = false
            topBar.visibility = View.VISIBLE
            topBar.animate().translationY(0f).alpha(1f).setDuration(300).setListener(null).start()
            bottomBar.visibility = View.VISIBLE
            bottomBar.animate().translationY(0f).alpha(1f).setDuration(300).setListener(null).start()
        }
        if (!isSettingsOpen) {
            hideHandler.postDelayed(hideUIRunnable, 4000)
        }
    }

    private fun hideUI() {
        if (isSettingsOpen || isUIHidden) return
        isUIHidden = true
        topBar.animate().translationY(-topBar.height.toFloat()).alpha(0f).setDuration(500)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    topBar.visibility = View.INVISIBLE
                }
            }).start()
        bottomBar.animate().translationY(bottomBar.height.toFloat()).alpha(0f).setDuration(500)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bottomBar.visibility = View.INVISIBLE
                }
            }).start()
    }

    private fun setUiStateConnecting() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            liveDot.clearAnimation()
            liveIndicator.setBackgroundResource(R.drawable.bg_live_indicator_connecting)
            liveDot.setBackgroundResource(R.drawable.shape_dot_connecting)
            liveText.setTextColor("#FEF08A".toColorInt())
            liveText.text = getString(R.string.state_connecting)
            
            babyIndicator.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    babyIndicator.visibility = View.GONE
                }
            }).start()
        }
    }

    private fun setUiStateLive() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            liveIndicator.setBackgroundResource(R.drawable.bg_live_indicator_live)
            liveDot.setBackgroundResource(R.drawable.shape_dot_live)
            liveText.setTextColor("#C4B5FD".toColorInt())
            liveText.text = getString(R.string.state_live)
            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            liveDot.startAnimation(pulse)
        }
    }

    private fun initWebRTC() {
        // Initialize WebRTC with field trials for low latency
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-IntelVP8/Enabled/") // Example trial
                .createInitializationOptions()
        )

        eglBaseContext = EglBase.create().eglBaseContext
        
        // Priority to Hardware Decoding for lower latency and better performance
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .createPeerConnectionFactory()

        surfaceView.init(eglBaseContext, null)
        surfaceView.setEnableHardwareScaler(true)
        surfaceView.setMirror(false)
    }

    private fun initAI() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun requestWifiNetwork() {
        setUiStateConnecting()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(apSsid)
            .setWpa2Passphrase(apPass)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .setNetworkSpecifier(specifier)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                runOnUiThread { startWebRTCStream() }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                connectivityManager.bindProcessToNetwork(null)
                setUiStateConnecting()
            }
        }
        connectivityManager.requestNetwork(request, networkCallback!!)
    }

    private fun startWebRTCStream() {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        // Optimization: Disable some internal buffers
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(p0: IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track() as? VideoTrack
                runOnUiThread {
                    track?.addSink(surfaceView)
                    // Add AI Sink
                    track?.addSink { frame -> analyzeFrame(frame) }
                    setUiStateLive()
                }
            }
        })
        
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))

        val constraints = MediaConstraints()
        // Low Latency Constraints
        constraints.optional.add(MediaConstraints.KeyValuePair("googCpuOveruseDetection", "false"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                // Optimization: Modify SDP to force low latency / immediate playback
                val optimizedSdp = sdp.description
                
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { sendWhepOffer(optimizedSdp) }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, SessionDescription(sdp.type, optimizedSdp))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun analyzeFrame(frame: VideoFrame) {
        val currentTime = System.currentTimeMillis()
        if (isAnalyzing || currentTime - lastAnalysisTime < 1000) return // Analyze once per second max

        isAnalyzing = true
        lastAnalysisTime = currentTime

        // SCALE DOWN: 1024x768 -> 320x240 for massive speedup
        val scaledBuffer = frame.buffer.cropAndScale(0, 0, frame.buffer.width, frame.buffer.height, 320, 240)
        frame.retain() // Retain the frame for its rotation info
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val buffer = scaledBuffer.toI420()
                scaledBuffer.release() // Release the scaled buffer immediately after conversion

                if (buffer != null) {
                    val width = buffer.width
                    val height = buffer.height
                    val argbPixels = IntArray(width * height)
                    val yBuffer = buffer.dataY
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val yVal = yBuffer.get(y * buffer.strideY + x).toInt() and 0xFF
                            argbPixels[y * width + x] = Color.rgb(yVal, yVal, yVal)
                        }
                    }
                    
                    val bitmap = Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
                    val inputImage = InputImage.fromBitmap(bitmap, frame.rotation)

                    faceDetector.process(inputImage)
                        .addOnCompleteListener {
                            buffer.release()
                            frame.release()
                            isAnalyzing = false
                        }
                        .addOnSuccessListener { faces ->
                            if (faces.isEmpty()) {
                                updateBabyState("detecting")
                                sleepingFramesCount = 0
                            } else {
                                val face = faces[0]
                                val leftOpen = face.leftEyeOpenProbability ?: -1f
                                val rightOpen = face.rightEyeOpenProbability ?: -1f
                                
                                if (leftOpen > 0.2f || rightOpen > 0.2f) {
                                    updateBabyState("awake")
                                    sleepingFramesCount = 0
                                } else if (leftOpen != -1f && rightOpen != -1f) {
                                    sleepingFramesCount++
                                    if (sleepingFramesCount >= requiredSleepingFrames) {
                                        updateBabyState("sleeping")
                                    }
                                }
                            }
                        }
                } else {
                    frame.release()
                    isAnalyzing = false
                }
            } catch (e: Exception) {
                frame.release()
                isAnalyzing = false
            }
        }
    }

    private fun updateBabyState(state: String) {
        runOnUiThread {
            when (state) {
                "awake" -> {
                    if (babyIndicator.visibility != View.GONE) {
                        babyIndicator.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                babyIndicator.visibility = View.GONE
                            }
                        }).start()
                    }
                    monitoringGlow.clearAnimation()
                    monitoringGlow.animate().alpha(0f).setDuration(500).start()
                }
                "detecting" -> {
                    if (monitoringGlow.animation == null) {
                        monitoringGlow.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (monitoringGlow.animation == null) {
                                    val glowPulse = AnimationUtils.loadAnimation(this@MainActivity, R.anim.glow_pulse)
                                    monitoringGlow.startAnimation(glowPulse)
                                }
                            }
                        }).start()
                    }
                    if (babyIndicator.visibility != View.GONE) {
                        babyIndicator.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                babyIndicator.visibility = View.GONE
                            }
                        }).start()
                    }
                }
                "sleeping" -> {
                    val sleepingText = getString(R.string.baby_state_sleeping)
                    if (babyText.text != sleepingText || babyIndicator.visibility != View.VISIBLE) {
                        babyText.text = sleepingText
                        babyIndicator.visibility = View.VISIBLE
                        babyIndicator.alpha = 0f
                        babyIndicator.animate().alpha(1f).setDuration(300).setListener(null).start()
                        babyIndicator.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    monitoringGlow.clearAnimation()
                    monitoringGlow.animate().alpha(0f).setDuration(500).start()
                }
            }
        }
    }

    private fun sendWhepOffer(sdp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(whepUrl)
                    .post(sdp.toRequestBody("application/sdp".toMediaType()))
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answerSdp = response.body.string()
                        val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, sessionDesc)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Connection Error", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(4, 3)).build()
        enterPictureInPictureMode(params)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideUIRunnable)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        connectivityManager.bindProcessToNetwork(null)
        peerConnection?.close()
        surfaceView.release()
        faceDetector.close()
    }
}

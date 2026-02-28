package com.bebecam

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Rational
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val logTag = "BebeCam"
    private val apSsid = "BebeCam"
    private val apPass = "bebecam_auto"
    private val dashboardUrl = "http://192.168.4.1:8080"

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Haptic feedback interface for the WebView
    inner class WebAppInterface {
        @Suppress("unused") // Used by the WebView
        @JavascriptInterface
        fun performHapticFeedback(effect: String) {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val vibrationEffect = when (effect) {
                "tick" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                "click" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                "snap" -> VibrationEffect.createOneShot(25, 190) // A refined, single pulse
                else -> {
                    val timings = effect.split(",").map { it.trim().toLong() }.toLongArray()
                    if (timings.isNotEmpty()) {
                        VibrationEffect.createWaveform(timings, -1)
                    } else {
                        null
                    }
                }
            }
            vibrationEffect?.let { vibrator.vibrate(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        connectivityManager = getSystemService<ConnectivityManager>()!!
        requestWifiNetwork()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // Allow autoplay video
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        // Expose the haptic feedback interface to the WebView
        webView.addJavascriptInterface(WebAppInterface(), "Android")
    }

    private fun requestWifiNetwork() {
        Log.d(logTag, "Requesting binding to specific Wi-Fi: $apSsid")

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
                Log.d(logTag, "Network Available: $network")
                // The core magic: This binds the app's WebView and WebRTC to strictly use
                // the BebeCam offline Wi-Fi, leaving the 4G/5G radio fully functional
                // for the rest of the Android OS (WhatsApp, Maps, etc.)
                connectivityManager.bindProcessToNetwork(network)
                
                // Load the Dashboard now that the native OS route is established
                runOnUiThread {
                    val urlWithCacheBuster = "$dashboardUrl?t=" + System.currentTimeMillis()
                    Log.d(logTag, "Loading BebeCam Dashboard: $urlWithCacheBuster")
                    webView.loadUrl(urlWithCacheBuster)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(logTag, "Network Lost")
                connectivityManager.bindProcessToNetwork(null)
            }
        }

        connectivityManager.requestNetwork(request, networkCallback!!)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter Picture-in-Picture mode when user presses the Home button
        Log.d(logTag, "Entering native Picture-in-Picture mode")
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(4, 3)) // Ideal aspect ratio for the camera feed
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        connectivityManager.bindProcessToNetwork(null)
    }
}

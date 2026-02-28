package com.bebecam

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Rational
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val TAG = "BebeCam"
    private val AP_SSID = "BebeCam"
    private val AP_PASS = "bebecam_auto"
    private val DASHBOARD_URL = "http://192.168.4.1:8080"

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Haptic feedback interface for the WebView
    inner class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun performHapticFeedback(effect: String) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Use O (API 26) for one-shot with amplitude
                val vibrationEffect = when (effect) {
                    "tick" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    } else {
                        VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    "click" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    } else {
                         VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    "snap" -> VibrationEffect.createOneShot(25, 190) // A refined, single pulse. 50% between tick and old max.
                    else -> {
                        // Fallback for custom patterns like "20,30"
                        val timings = effect.split(",").map { it.trim().toLong() }.toLongArray()
                        if (timings.isNotEmpty()) {
                            VibrationEffect.createWaveform(timings, -1)
                        } else {
                            null
                        }
                    }
                }
                vibrationEffect?.let { vibrator.vibrate(it) }
            } else {
                // Fallback for older APIs
                @Suppress("DEPRECATION")
                when (effect) {
                    "tick" -> vibrator.vibrate(5)
                    "click" -> vibrator.vibrate(10)
                    "snap" -> vibrator.vibrate(25) // A single, medium vibration
                    else -> {
                        val timings = effect.split(",").map { it.trim().toLong() }.toLongArray()
                        if (timings.isNotEmpty()) {
                            vibrator.vibrate(timings, -1)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
    }

    private fun requestWifiNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Requesting binding to specific Wi-Fi: $AP_SSID")

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(AP_SSID)
                .setWpa2Passphrase(AP_PASS)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Network Available: $network")
                    // The core magic: This binds the app's WebView and WebRTC to strictly use
                    // the BebeCam offline Wi-Fi, leaving the 4G/5G radio fully functional
                    // for the rest of the Android OS (WhatsApp, Maps, etc.)
                    connectivityManager.bindProcessToNetwork(network)
                    
                    // Load the Dashboard now that the native OS route is established
                    runOnUiThread {
                        val urlWithCacheBuster = "$DASHBOARD_URL?t=" + System.currentTimeMillis()
                        Log.d(TAG, "Loading BebeCam Dashboard: $urlWithCacheBuster")
                        webView.loadUrl(urlWithCacheBuster)
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d(TAG, "Network Lost")
                    connectivityManager.bindProcessToNetwork(null)
                }
            }

            connectivityManager.requestNetwork(request, networkCallback!!)
        } else {
            // Devices older than Android 10 do not support WifiNetworkSpecifier lockouts.
            // Just load the view immediately.
            val urlWithCacheBuster = "$DASHBOARD_URL?t=" + System.currentTimeMillis()
            Log.w(TAG, "Android version too old for NetworkSpecifier, attempting simple load of $urlWithCacheBuster")
            webView.loadUrl(urlWithCacheBuster)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter Picture-in-Picture mode when user presses the Home button
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Entering native Picture-in-Picture mode")
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(4, 3)) // Ideal aspect ratio for the camera feed
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        connectivityManager.bindProcessToNetwork(null)
    }
}

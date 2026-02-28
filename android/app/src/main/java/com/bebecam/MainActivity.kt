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
import android.util.Log
import android.util.Rational
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
                        Log.d(TAG, "Loading BebeCam Dashboard: $DASHBOARD_URL")
                        webView.loadUrl(DASHBOARD_URL)
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
            Log.w(TAG, "Android version too old for NetworkSpecifier, attempting simple load")
            webView.loadUrl(DASHBOARD_URL)
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

package com.yourname.helmx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.yourname.helmx.databinding.ActivityNavigationBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class NavigationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    
    private var useHelmetHardwareLocation = false

    // Search Autocomplete Components
    private lateinit var suggestionAdapter: ArrayAdapter<String>
    private val currentSuggestionsInfo = mutableListOf<Pair<String, Pair<Double, Double>>>()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isResolvingLocation = false
    private var wasJustResolving = false
    private var lastHandledDestination = ""
    private var pendingAutoNavDestination: String? = null
    private var isNavigating = false
    private var isAwaitingAutoNavStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        textToSpeech = TextToSpeech(this, this)

        setupWebView()
        setupListeners()
        setupBottomNavigation()
        observeHelmetData()
    }

    private fun observeHelmetData() {
        val helmetBleManager = HelmetBleManager.getInstance(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                helmetBleManager.helmetData.collectLatest { data ->
                    if (data.destination.isNotEmpty() && data.destination != lastHandledDestination) {
                        lastHandledDestination = data.destination
                        binding.etSearch.setText(data.destination)
                        
                        // Automatically try to start navigation
                        if (currentLat != 0.0 && currentLng != 0.0) {
                            pendingAutoNavDestination = null
                            isAwaitingAutoNavStart = true
                            initiateGeocodeAndRoute(data.destination)
                        } else {
                            // If location is zero, try to fetch it first
                            pendingAutoNavDestination = data.destination
                            isAwaitingAutoNavStart = true
                            Toast.makeText(this@NavigationActivity, "Auto-nav: Acquiring GPS...", Toast.LENGTH_SHORT).show()
                            requestPermissionsForNavigation()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_navigation
        if (!isResolvingLocation && !wasJustResolving) {
            requestPermissionsForNavigation()
        }
        wasJustResolving = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            isResolvingLocation = false
            wasJustResolving = true
            if (resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location services are required for this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndPromptForLocationServices() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 10000
        ).build()

        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = com.google.android.gms.location.LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize location requests here.
            startLocationUpdates()
        }

        task.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                try {
                    isResolvingLocation = true
                    exception.startResolutionForResult(this@NavigationActivity, 1001)
                } catch (sendEx: android.content.IntentSender.SendIntentException) {
                    isResolvingLocation = false
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).build()

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                for (location in locationResult.locations) {
                    supplyCoordinatesToMap(location.latitude, location.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webViewMap.settings.javaScriptEnabled = true
        binding.webViewMap.settings.domStorageEnabled = true
        binding.webViewMap.webViewClient = WebViewClient()
        binding.webViewMap.webChromeClient = WebChromeClient()
        binding.webViewMap.addJavascriptInterface(WebAppInterface(this), "Android")
        binding.webViewMap.loadUrl("file:///android_asset/leaflet_map.html")
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Setting up Nominatim AutoComplete with Custom Filter to bypass strict prefix matching
        suggestionAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mutableListOf()) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults().apply {
                            values = currentSuggestionsInfo.map { it.first }
                            count = currentSuggestionsInfo.size
                        }
                    }
                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        if (results != null && results.count > 0) {
                            notifyDataSetChanged()
                        } else {
                            notifyDataSetInvalidated()
                        }
                    }
                }
            }
        }
        binding.etSearch.setAdapter(suggestionAdapter)

        binding.etSearch.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString()
                if (query.length < 3) return
                
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { fetchNominatimSuggestions(query) }
                searchHandler.postDelayed(searchRunnable!!, 800)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etSearch.setOnItemClickListener { _, _, position, _ ->
            if (position < currentSuggestionsInfo.size) {
                val selected = currentSuggestionsInfo[position]
                binding.etSearch.setText(selected.first)
                UtilsCloseKeyboard()
                
                Toast.makeText(this, "Calculating Route...", Toast.LENGTH_SHORT).show()
                initiateRoutingToLocationDirectly(selected.second.first, selected.second.second)
            }
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            if (query.isNotEmpty()) {
                UtilsCloseKeyboard()
                initiateGeocodeAndRoute(query)
            } else {
                Toast.makeText(this, "Enter a destination.", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.fabMyLocation.setOnClickListener {
            if (currentLat != 0.0 && currentLng != 0.0) {
                supplyCoordinatesToMap(currentLat, currentLng)
            } else {
                fetchGpsLocation()
            }
        }

        binding.btnVoice.setOnClickListener {
            startVoiceRecognition()
        }

        binding.btnStartNav.setOnClickListener {
            toggleNavigation(!isNavigating)
        }
    }

    private fun toggleNavigation(start: Boolean) {
        if (start) {
            isNavigating = true
            binding.btnStartNav.text = "STOP"
            binding.btnStartNav.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DC2626"))
            binding.cardSearch.visibility = View.GONE
            binding.webViewMap.evaluateJavascript("startActiveNavigation()", null)
        } else {
            isNavigating = false
            binding.btnStartNav.text = "START"
            binding.btnStartNav.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00B4D8"))
            binding.cardSearch.visibility = View.VISIBLE
            binding.webViewMap.evaluateJavascript("stopActiveNavigation()", null)
        }
    }
    
    private fun UtilsCloseKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun fetchGpsLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
        if (!useHelmetHardwareLocation) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    supplyCoordinatesToMap(it.latitude, it.longitude)
                } ?: run {
                    Toast.makeText(this, "Awaiting GPS Signal...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Abstracting to ensure routing functions correctly when HelmX is disconnected
    private fun supplyCoordinatesToMap(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
        binding.webViewMap.evaluateJavascript("updateLocation($lat, $lng)", null)
        
        // If we were waiting for GPS to start a route, do it now
        pendingAutoNavDestination?.let { dest ->
            pendingAutoNavDestination = null
            initiateGeocodeAndRoute(dest)
        }
    }

    // --- Native AutoComplete Search Engine (Bypasses WebView HTTP Blocks) ---
    private fun fetchNominatimSuggestions(query: String) {
        thread {
            try {
                val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=5&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "HelmXApp/1.0") // Required by Nominatim policy
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    val newDisplay = mutableListOf<String>()
                    val newData = mutableListOf<Pair<String, Pair<Double, Double>>>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val displayName = obj.getString("display_name")
                        val lat = obj.getString("lat").toDouble()
                        val lon = obj.getString("lon").toDouble()
                        
                        newDisplay.add(displayName)
                        newData.add(Pair(displayName, Pair(lat, lon)))
                    }
                    
                    runOnUiThread {
                        currentSuggestionsInfo.clear()
                        currentSuggestionsInfo.addAll(newData)
                        suggestionAdapter.clear()
                        suggestionAdapter.addAll(newDisplay)
                        suggestionAdapter.notifyDataSetChanged()
                        if (newDisplay.isNotEmpty()) {
                            binding.etSearch.showDropDown()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initiateGeocodeAndRoute(destination: String) {
        if (currentLat == 0.0 || currentLng == 0.0) {
            Toast.makeText(this, "Acquiring GPS Signal before route...", Toast.LENGTH_SHORT).show()
            fetchGpsLocation()
            return
        }
        
        Toast.makeText(this, "Processing destination...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${java.net.URLEncoder.encode(destination, "UTF-8")}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "HelmXApp/1.0")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    if (jsonArray.length() > 0) {
                        val obj = jsonArray.getJSONObject(0)
                        val endLat = obj.getString("lat").toDouble()
                        val endLng = obj.getString("lon").toDouble()
                        
                        runOnUiThread {
                            initiateRoutingToLocationDirectly(endLat, endLng)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this@NavigationActivity, "Destination not found.", Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initiateRoutingToLocationDirectly(destLat: Double, destLng: Double) {
        if (currentLat == 0.0 || currentLng == 0.0) {
            Toast.makeText(this, "Error: Location null. Press Crosshair to fetch GPS.", Toast.LENGTH_LONG).show()
            return
        }
        // Passes confirmed lat/long combinations directly into the Leaflet algorithm.
        binding.webViewMap.evaluateJavascript("routeTo($currentLat, $currentLng, $destLat, $destLng)", null)
    }

    // --- Voice Recognition Systems ---
    private fun startVoiceRecognition() {
         if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required for voice.", Toast.LENGTH_SHORT).show()
            return
         }
         
         speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
         val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
             putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
             putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
             putExtra(RecognizerIntent.EXTRA_PROMPT, "Where to?")
         }
         
         speechRecognizer.setRecognitionListener(object: RecognitionListener {
             override fun onReadyForSpeech(params: Bundle?) {
                 Toast.makeText(this@NavigationActivity, "Listening...", Toast.LENGTH_SHORT).show()
             }
             override fun onBeginningOfSpeech() {}
             override fun onRmsChanged(rmsdB: Float) {}
             override fun onBufferReceived(buffer: ByteArray?) {}
             override fun onEndOfSpeech() {}
             override fun onError(error: Int) {
                 Toast.makeText(this@NavigationActivity, "Voice error: $error", Toast.LENGTH_SHORT).show()
             }
             override fun onResults(results: Bundle?) {
                 val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                 if (!matches.isNullOrEmpty()) {
                     val spokenText = matches[0]
                     binding.etSearch.setText(spokenText)
                     UtilsCloseKeyboard()
                     initiateGeocodeAndRoute(spokenText)
                 }
             }
             override fun onPartialResults(partialResults: Bundle?) {}
             override fun onEvent(eventType: Int, params: Bundle?) {}
         })
         
         speechRecognizer.startListening(intent)
    }

    // --- Android TTS Initialiser ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
        }
    }
    
    private fun playAudibleInstruction(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NavCommand")
    }

    // --- Data Bridge handling bidirectional Javascript-Kotlin Communication ---
    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun speakInstruction(instruction: String) {
            runOnUiThread {
                playAudibleInstruction(instruction)
            }
        }
        
        @JavascriptInterface
        fun onRouteCalculated(distance: String, time: String) {
            runOnUiThread {
                binding.cardRouteInfo.visibility = View.VISIBLE
                binding.tvDistance.text = "Distance: $distance km"
                binding.tvDuration.text = "Time: $time mins"
                
                // If this was an auto-nav request, start navigation automatically
                if (isAwaitingAutoNavStart) {
                    isAwaitingAutoNavStart = false
                    toggleNavigation(true)
                }
            }
        }

        @JavascriptInterface
        fun onRouteError(errorMsg: String) {
             runOnUiThread {
                 Toast.makeText(mContext, errorMsg, Toast.LENGTH_SHORT).show()
             }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            checkAndPromptForLocationServices()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissionsForNavigation() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            checkAndPromptForLocationServices()
        } else {
            locationPermissionRequest.launch(permissions)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = android.content.Intent(this, DashboardActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_analytics -> {
                    val intent = android.content.Intent(this, AnalyticsActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                R.id.nav_navigation -> true
                R.id.nav_settings -> {
                    val intent = android.content.Intent(this, SettingsActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
            try {
                // Ensure handlers are cleared dynamically avoiding memory leaks during rapid teardown
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
            } catch (ignore: Exception) {}
        }
        super.onDestroy()
    }
}
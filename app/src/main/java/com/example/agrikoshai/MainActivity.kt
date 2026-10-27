package com.example.agrikoshai

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.tensorflow.lite.Interpreter

data class Message(val role: String, val content: String)
data class ChatSession(val id: String, var title: String, val messages: MutableList<Message>)
data class ChatRequest(val target_language: String, val messages: List<Message>)
data class ChatResponse(val answer: String)
data class WaterResponse(val location: String, val current_depth_m: Double, val predicted_depth_m: Double, val advisory: String)
data class Commodity(val name: String, val price: String, val trend: String)
data class MandiResponse(val market: String, val date: String, val commodities: List<Commodity>, val advisory: String)

data class HourlyItem(val time: String, val temp: String, val rain_prob: String, val rain_val: Int, val icon: String)
data class DailyItem(val day: String, val temp_range: String, val icon: String)
data class WeatherResponse(
    val location_name: String,
    val current_temp: String,
    val condition_text: String,
    val feels_like: String,
    val precipitation_header: String,
    val advisory: String,
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>
)

data class SchemeOption(val value: String, val label: Map<String, String>)
data class SchemeCriteria(val questionId: String, val questionText: Map<String, String>, val inputType: String, val options: List<SchemeOption>?, val operator: String, val targetValue: JsonElement?)
data class AgriScheme(val schemeId: String, val name: Map<String, String>, val level: String, val state: String, val benefit: Map<String, String>, val criteria: List<SchemeCriteria>)

data class TopClass(val label: String, val probability: Double)
data class DiseaseResponse(val status: String, val prediction: String?, val advisory: String?, val message: String?, val top_classes: List<TopClass>?)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )
    if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
        ActivityCompat.requestPermissions(this, permissions, 1)
    }

    drawerLayout = findViewById(R.id.drawerLayout)
    historyContainer = findViewById(R.id.historyContainer)
    mainAppLayout = findViewById(R.id.mainAppLayout)
    languageSelectorLayout = findViewById(R.id.languageSelectorLayout)
    bottomNavLayout = findViewById(R.id.bottomNavLayout)

    layoutChat = findViewById(R.id.layoutChat)
    layoutMandi = findViewById(R.id.layoutMandi)
    layoutForecast = findViewById(R.id.layoutForecast)
    layoutSchemes = findViewById(R.id.layoutSchemes)
    layoutDisease = findViewById(R.id.layoutDisease)

    btnToggleWater = findViewById(R.id.btnToggleWater)
    layoutWaterContent = findViewById(R.id.layoutWaterContent)
    btnToggleWeather = findViewById(R.id.btnToggleWeather)
    layoutWeatherContent = findViewById(R.id.layoutWeatherContent)

    btnToggleWater?.setOnClickListener { toggleSection(layoutWaterContent, btnToggleWater) }
    btnToggleWeather?.setOnClickListener { toggleSection(layoutWeatherContent, btnToggleWeather) }

    layoutWaterContent?.visibility = View.GONE
    layoutWeatherContent?.visibility = View.GONE
    btnToggleWater?.text = "▼"
    btnToggleWeather?.text = "▼"

    voiceOrb = findViewById(R.id.voiceOrb)
    tvStatus = findViewById(R.id.tvStatus)
    chatContainer = findViewById(R.id.chatContainer)
    chatScrollView = findViewById(R.id.chatScrollView)
    etMessage = findViewById(R.id.etMessage)
    btnSend = findViewById(R.id.btnSend)

    spinnerDistrict = findViewById(R.id.spinnerDistrict)
    spinnerMarket = findViewById(R.id.spinnerMarket)
    spinnerCrop = findViewById(R.id.spinnerCrop)
    tvMandiData = findViewById(R.id.tvMandiData)

    tvWaterData = findViewById(R.id.tvWaterData)
    tvWeatherLocation = findViewById(R.id.tvWeatherLocation)
    tvCurrentTemp = findViewById(R.id.tvCurrentTemp)
    tvWeatherCondition = findViewById(R.id.tvWeatherCondition)
    tvFeelsLike = findViewById(R.id.tvFeelsLike)
    containerHourly = findViewById(R.id.containerHourly)
    containerDaily = findViewById(R.id.containerDaily)
    containerRain = findViewById(R.id.containerRain)
    tvPrecipitationHeader = findViewById(R.id.tvPrecipitationHeader)
    tvWeatherAdvisory = findViewById(R.id.tvWeatherAdvisory)

    ivDiseasePreview = findViewById(R.id.ivDiseasePreview)
    tvDiseaseResult = findViewById(R.id.tvDiseaseResult)

    spinnerSchemeState = findViewById(R.id.spinnerSchemeState)
    layoutSchemeStateSelection = findViewById(R.id.layoutSchemeStateSelection)
    layoutSchemeQuestionEngine = findViewById(R.id.layoutSchemeQuestionEngine)
    layoutSchemeResults = findViewById(R.id.layoutSchemeResults)
    tvDynamicQuestionText = findViewById(R.id.tvDynamicQuestionText)
    tvDynamicQuestionHeader = findViewById(R.id.tvDynamicQuestionHeader)
    containerDynamicInput = findViewById(R.id.containerDynamicInput)
    containerSchemesResult = findViewById(R.id.containerSchemesResult)

    val marketMap = mapOf(
        "Ludhiana" to arrayOf("Ludhiana", "Khanna", "Jagraon", "Doraha", "Samrala", "Mullanpur"),
        "Amritsar" to arrayOf("Amritsar", "Ajnala", "Rayya", "Majitha", "Attari"),
        "Patiala" to arrayOf("Patiala", "Rajpura", "Nabha", "Samana", "Patran"),
        "S.A.S Nagar" to arrayOf("Kharar", "Kurali", "Lalru", "Derabassi", "Banur"),
        "Jalandhar" to arrayOf("Jalandhar City", "Nakodar", "Phillaur", "Shahkot", "Adampur"),
        "Bhatinda" to arrayOf("Bhatinda", "Rampura Phul", "Maur", "Goniana"),
        "Gurdaspur" to arrayOf("Gurdaspur", "Batala", "Dhariwal", "Dinanagar"),
        "Ferozpur" to arrayOf("Ferozpur City", "Zira", "Guruharsahai"),
        "Moga" to arrayOf("Moga", "Baghapurana", "Dharamkot", "Nihal Singh Wala"),
        "Sangrur" to arrayOf("Sangrur", "Sunam", "Dhuri", "Malerkotla", "Ahmedgarh")
    )
    val districts = marketMap.keys.toTypedArray()
    val crops = arrayOf("Wheat", "Paddy(Dhan)", "Maize", "Cotton", "Mustard", "Potato", "Onion", "Tomato", "Cauliflower", "Cabbage", "Carrot", "Green Chilli", "Apple", "Banana", "Garlic", "Pulses", "Coconut", "Vegetables", "Fruits")
    val statesList = arrayOf("Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry")

    spinnerDistrict.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, districts)
    spinnerCrop.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, crops)
    spinnerSchemeState.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statesList)

    spinnerDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            spinnerMarket.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, marketMap[districts[position]] ?: arrayOf("Main Mandi"))
        }
        override fun onNothingSelected(parent: AdapterView<*>) {}
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    apiService = Retrofit.Builder().baseUrl(serverUrl).client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)

    val rootView = findViewById<View>(android.R.id.content)
    rootView.viewTreeObserver.addOnGlobalLayoutListener {
        val r = android.graphics.Rect()
        rootView.getWindowVisibleDisplayFrame(r)
        val screenHeight = rootView.height
        val keypadHeight = screenHeight - r.bottom

        if (keypadHeight > screenHeight * 0.15) {
            bottomNavLayout.visibility = View.GONE
            chatScrollView.postDelayed({ chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) }, 100)
        } else {
            if (mainAppLayout.visibility == View.VISIBLE) {
                bottomNavLayout.visibility = View.VISIBLE
                etMessage.clearFocus()
            }
        }
    }

    loadTFLiteModel()
    setupNavigation()
    setupButtons()
    setupLanguageButtons()

    val btnCloseLanguage = findViewById<TextView>(R.id.btnCloseLanguage)
    btnCloseLanguage.setOnClickListener { showMainApp() }

    val prefs = getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE)
    currentLanguageCode = prefs.getString("LangCode", "") ?: ""
    currentLanguageName = prefs.getString("LangName", "") ?: ""

    if (currentLanguageCode.isNotEmpty()) {
        applyUILanguage()
        showMainApp()
        loadSessionsFromPrefs()
    } else {
        languageSelectorLayout.visibility = View.VISIBLE
        btnCloseLanguage.visibility = View.GONE
    }
}

private fun loadTFLiteModel() {
    try {
        val fileDescriptor = assets.openFd("model.tflite")
        val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val modelBuffer = fileChannel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        tflite = Interpreter(modelBuffer)
        tfliteLabels = assets.open("labels.txt").bufferedReader().readLines()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun toggleSection(contentLayout: LinearLayout?, toggleIcon: TextView?) {
    if (contentLayout?.visibility == View.VISIBLE) {
        contentLayout.visibility = View.GONE
        toggleIcon?.text = "▼"
    } else {
        contentLayout?.visibility = View.VISIBLE
        toggleIcon?.text = "▲"
    }
}

private fun loadSessionsFromPrefs() {
    val prefs = getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE)
    val json = prefs.getString("ChatHistory", null)
    if (json != null) {
        val type = object : TypeToken<MutableList<ChatSession>>() {}.type
        allSessions = gson.fromJson(json, type)
    }

    if (allSessions.isEmpty()) {
        createNewSession()
    } else {
        loadSession(allSessions.first().id)
    }
}

private fun saveSessionsToPrefs() {
    val prefs = getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("ChatHistory", gson.toJson(allSessions)).apply()
    updateDrawerUI()
}

private fun createNewSession() {
    currentSessionId = System.currentTimeMillis().toString()
    allSessions.add(0, ChatSession(currentSessionId, t("New Chat"), mutableListOf()))
    saveSessionsToPrefs()

    chatHistoryList.clear()
    chatContainer.removeAllViews()
    val welcomeGreeting = if(currentLanguageName == "English" || currentLanguageName.isEmpty()) "Welcome to AgriKosh. I am ready to help in English." else "मैं $currentLanguageName में आपकी मदद करने के लिए तैयार हूं।"
    addChatBubble(welcomeGreeting, isUser = false)
}

private fun loadSession(id: String) {
    val session = allSessions.find { it.id == id } ?: return
    currentSessionId = id

    chatHistoryList.clear()
    chatHistoryList.addAll(session.messages)

    chatContainer.removeAllViews()
    val welcomeGreeting = if(currentLanguageName == "English" || currentLanguageName.isEmpty()) "Welcome to AgriKosh. I am ready to help in English." else "मैं $currentLanguageName में आपकी मदद करने के लिए तैयार हूं।"
    addChatBubble(welcomeGreeting, isUser = false)

    for (msg in session.messages) {
        addChatBubble(msg.content, isUser = msg.role == "user")
    }

    drawerLayout.closeDrawer(GravityCompat.START)
    updateDrawerUI()
}

private fun deleteSession(id: String) {
    allSessions.removeAll { it.id == id }
    if (allSessions.isEmpty()) {
        createNewSession()
    } else {
        saveSessionsToPrefs()
        if (id == currentSessionId) {
            loadSession(allSessions.first().id)
        }
    }
}

private fun updateDrawerUI() {
    historyContainer.removeAllViews()
    for (session in allSessions.toList()) {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)

            if (session.id == currentSessionId) {
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#2E2836"))
                bg.cornerRadius = 16f
                background = bg
            }
        }

        val tvTitle = TextView(this).apply {
            text = "💬 ${session.title}"
            setTextColor(if (session.id == currentSessionId) Color.WHITE else Color.parseColor("#AFAFAF"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { loadSession(session.id) }
        }

        val btnDelete = TextView(this).apply {
            text = "🗑️"
            textSize = 16f
            setPadding(16, 8, 8, 8)
            setOnClickListener { deleteSession(session.id) }
        }

        rowLayout.addView(tvTitle)
        rowLayout.addView(btnDelete)
        historyContainer.addView(rowLayout)
    }
}

private fun setupNavigation() {
    fun switchTab(activeLayout: View) {
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
            etMessage.clearFocus()
        }
        layoutChat.visibility = View.GONE
        layoutMandi.visibility = View.GONE
        layoutForecast.visibility = View.GONE
        layoutSchemes.visibility = View.GONE
        layoutDisease.visibility = View.GONE
        activeLayout.visibility = View.VISIBLE
    }

    findViewById<LinearLayout>(R.id.tabChat).setOnClickListener { switchTab(layoutChat) }
    findViewById<LinearLayout>(R.id.tabDisease).setOnClickListener { switchTab(layoutDisease) }
    findViewById<LinearLayout>(R.id.tabMandi).setOnClickListener { switchTab(layoutMandi) }
    findViewById<LinearLayout>(R.id.tabForecast).setOnClickListener {
        switchTab(layoutForecast)
        layoutWeatherContent?.visibility = View.VISIBLE
        btnToggleWeather?.text = "▲"
        checkGroundwaterLevels()
        checkLocationAndFetchWeather()
    }
    findViewById<LinearLayout>(R.id.tabSchemes).setOnClickListener { switchTab(layoutSchemes) }
}

private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
    if (bitmap != null) {
        ivDiseasePreview.setImageBitmap(bitmap)
        processDiseaseImage(bitmap)
    }
}

private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        ivDiseasePreview.setImageBitmap(bitmap)
        processDiseaseImage(bitmap)
    }
}

private fun setupLanguageButtons() {
    val clickListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.btnLangHindi -> setLanguage("hi-IN", "Hindi")
            R.id.btnLangPunjabi -> setLanguage("pa-IN", "Punjabi")
            R.id.btnLangMarathi -> setLanguage("mr-IN", "Marathi")
            R.id.btnLangBengali -> setLanguage("bn-IN", "Bengali")
            R.id.btnLangEnglish -> setLanguage("en-IN", "English")
        }
    }
    findViewById<Button>(R.id.btnLangHindi).setOnClickListener(clickListener)
    findViewById<Button>(R.id.btnLangPunjabi).setOnClickListener(clickListener)
    findViewById<Button>(R.id.btnLangMarathi).setOnClickListener(clickListener)
    findViewById<Button>(R.id.btnLangBengali).setOnClickListener(clickListener)
    findViewById<Button>(R.id.btnLangEnglish).setOnClickListener(clickListener)
}

private fun setLanguage(code: String, name: String) {
    currentLanguageCode = code; currentLanguageName = name
    getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE).edit().putString("LangCode", code).putString("LangName", name).apply()
    applyUILanguage()
    showMainApp()
    val locale = Locale.forLanguageTag(currentLanguageCode)
    if (::tts.isInitialized && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) { tts.setLanguage(locale) }
    setupSpeechRecognizer()
}

private fun showMainApp() {
    languageSelectorLayout.visibility = View.GONE
    mainAppLayout.visibility = View.VISIBLE
    bottomNavLayout.visibility = View.VISIBLE
    if(!::tts.isInitialized) { tts = TextToSpeech(this, this); setupSpeechRecognizer() }
}

private fun setupSpeechRecognizer() {
    if(::speechRecognizer.isInitialized) { speechRecognizer.destroy() }
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { tvStatus.text = t("Listening in $currentLanguageName..."); tvStatus.setTextColor(Color.parseColor("#00E676")) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { val scale = 1f + (rmsdB.coerceAtLeast(0f) / 12f); voiceOrb.animate().scaleX(scale).scaleY(scale).setDuration(50).start() }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { if (!forceStopListening && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) { startListening() } else { tvStatus.text = t("Tap to Speak (Tap again to stop)"); tvStatus.setTextColor(Color.WHITE); resetOrb(); isListening = false } }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.get(0) ?: ""
            if (spokenText.isNotEmpty()) { val currentInput = etMessage.text.toString(); val newText = if (currentInput.isEmpty()) spokenText else "$currentInput $spokenText"; etMessage.setText(newText); etMessage.setSelection(etMessage.text.length) }
            if (!forceStopListening) startListening() else { tvStatus.text = t("Tap to Speak (Tap again to stop)"); tvStatus.setTextColor(Color.WHITE); isListening = false }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
}

private fun startListening() { tts.stop(); val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode); speechRecognizer.startListening(intent); isListening = true }
private fun stopListening() { speechRecognizer.stopListening(); resetOrb(); isListening = false; tvStatus.text = t("Tap to Speak (Tap again to stop)"); tvStatus.setTextColor(Color.WHITE) }
private fun resetOrb() { voiceOrb.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }

private fun addChatBubble(message: String, isUser: Boolean) {
    val textView = TextView(this).apply {
        text = message; textSize = 15f; setPadding(48, 32, 48, 32)
        val bg = GradientDrawable()
        if (isUser) {
            bg.setColor(Color.parseColor("#3F51B5")); bg.cornerRadii = floatArrayOf(50f, 50f, 50f, 50f, 0f, 0f, 50f, 50f); setTextColor(Color.WHITE)
            setOnLongClickListener { showEditCopyDialog(message); true }
        } else {
            bg.setColor(Color.parseColor("#2E2836")); bg.cornerRadii = floatArrayOf(50f, 50f, 50f, 50f, 50f, 50f, 0f, 0f); setTextColor(Color.parseColor("#E0E0E0"))
            setOnLongClickListener { showAssistantOptionsDialog(message); true }
        }
        background = bg; val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.setMargins(0, 16, 0, 16); params.gravity = if (isUser) Gravity.END else Gravity.START; layoutParams = params
    }
    chatContainer.addView(textView); chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
}

private fun showEditCopyDialog(originalMessage: String) {
    AlertDialog.Builder(this).setTitle("Options").setItems(arrayOf("Copy Text", "Edit & Resend")) { _, which ->
        when (which) {
            0 -> { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("AgriKosh", originalMessage)); Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() }
            1 -> { etMessage.setText(originalMessage); etMessage.setSelection(originalMessage.length); etMessage.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT) }
        }
    }.show()
}

private fun showAssistantOptionsDialog(originalMessage: String) {
    AlertDialog.Builder(this).setTitle("Options").setItems(arrayOf("Copy Text", "Read Aloud")) { _, which ->
        when (which) {
            0 -> { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("AgriKosh", originalMessage)); Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() }
            1 -> { if (::tts.isInitialized) { tts.speak(originalMessage, TextToSpeech.QUEUE_FLUSH, null, "") } }
        }
    }.show()
}

private fun sendToKisanSLM() {
    val sessionAtRequestTime = currentSessionId
    val request = ChatRequest(currentLanguageName, chatHistoryList.toList())

    apiService.chatWithKisan(request).enqueue(object : Callback<ChatResponse> {
        override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
            if (response.isSuccessful) {
                val answer = response.body()?.answer ?: "Error processing request."
                val newMsg = Message("assistant", answer)

                if (sessionAtRequestTime == currentSessionId) {
                    addChatBubble(answer, isUser = false)
                    chatHistoryList.add(newMsg)
                    val session = allSessions.find { it.id == currentSessionId }
                    if (session != null) { session.messages.add(newMsg); saveSessionsToPrefs() }
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "")
                } else {
                    val oldSession = allSessions.find { it.id == sessionAtRequestTime }
                    if (oldSession != null) { oldSession.messages.add(newMsg); saveSessionsToPrefs() }
                }
            } else {
                if (sessionAtRequestTime == currentSessionId) addChatBubble("Connection Failed: Server Error ${response.code()}", isUser = false)
            }
        }
        override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
            if (sessionAtRequestTime == currentSessionId) {
                val errorMessage = if (t is java.net.SocketTimeoutException) "Connection Failed: The AI took too long to process (Timeout)." else "Connection Failed: ${t.localizedMessage}"
                addChatBubble(errorMessage, isUser = false)
            }
        }
    })
}

override fun onBackPressed() {
    if (languageSelectorLayout.visibility == View.VISIBLE && currentLanguageCode.isNotEmpty()) { showMainApp() } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) { drawerLayout.closeDrawer(GravityCompat.START) } else { super.onBackPressed() }
}

override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS && currentLanguageCode.isNotEmpty()) { val locale = Locale.forLanguageTag(currentLanguageCode); if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(locale) }
}

override fun onDestroy() {
    if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
    if (::speechRecognizer.isInitialized) { speechRecognizer.destroy() }
    super.onDestroy()
}
interface ApiService {
    @retrofit2.http.POST("/chat") fun chatWithKisan(@retrofit2.http.Body request: ChatRequest): Call<ChatResponse>
    @retrofit2.http.GET("/groundwater") fun checkGroundwater(@retrofit2.http.Query("lang") lang: String): Call<WaterResponse>
    @retrofit2.http.GET("/mandi") fun getMandiPrices(@retrofit2.http.Query("district") district: String, @retrofit2.http.Query("market") market: String, @retrofit2.http.Query("commodity") commodity: String, @retrofit2.http.Query("lang") lang: String): Call<MandiResponse>
    @retrofit2.http.GET("/weather") fun getWeather(@retrofit2.http.Query("lat") lat: Double, @retrofit2.http.Query("lon") lon: Double, @retrofit2.http.Query("city") city: String, @retrofit2.http.Query("lang") lang: String): Call<WeatherResponse>
    @retrofit2.http.Multipart @retrofit2.http.POST("/predict-disease") fun predictDisease(@retrofit2.http.Part file: MultipartBody.Part): Call<DiseaseResponse>
}
}

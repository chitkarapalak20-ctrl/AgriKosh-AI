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
import android.util.Log
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
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.tensorflow.lite.DataType
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

// Updated JSON structure for dynamic multilingual schemes
data class SchemeOption(
    val value: String,
    val label: Map<String, String>
)

data class SchemeCriteria(
    val questionId: String,
    val questionText: Map<String, String>,
    val inputType: String,
    val options: List<SchemeOption>?,
    val operator: String,
    val targetValue: JsonElement?
)

data class AgriScheme(
    val schemeId: String,
    val name: Map<String, String>,
    val level: String,
    val state: String,
    val benefit: Map<String, String>,
    val criteria: List<SchemeCriteria>
)

// Disease Prediction API Models
data class TopClass(val label: String, val probability: Double)
data class DiseaseResponse(
    val status: String,
    val prediction: String?,
    val advisory: String?,
    val message: String?,
    val top_classes: List<TopClass>?
)

interface ApiService {
    @retrofit2.http.POST("/chat") fun chatWithKisan(@retrofit2.http.Body request: ChatRequest): Call<ChatResponse>

    @retrofit2.http.GET("/groundwater") fun checkGroundwater(
        @retrofit2.http.Query("lang") lang: String
    ): Call<WaterResponse>

    @retrofit2.http.GET("/mandi") fun getMandiPrices(
        @retrofit2.http.Query("district") district: String,
        @retrofit2.http.Query("market") market: String,
        @retrofit2.http.Query("commodity") commodity: String,
        @retrofit2.http.Query("lang") lang: String
    ): Call<MandiResponse>

    @retrofit2.http.GET("/weather") fun getWeather(
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lon") lon: Double,
        @retrofit2.http.Query("city") city: String,
        @retrofit2.http.Query("lang") lang: String
    ): Call<WeatherResponse>

    @retrofit2.http.Multipart
    @retrofit2.http.POST("/predict-disease")
    fun predictDisease(
        @retrofit2.http.Part file: MultipartBody.Part
    ): Call<DiseaseResponse>
}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var apiService: ApiService
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    // TFLite Gatekeeper
    private var tflite: Interpreter? = null
    private var tfliteLabels: List<String> = emptyList()

    // Layouts
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var mainAppLayout: FrameLayout
    private lateinit var languageSelectorLayout: LinearLayout
    private lateinit var bottomNavLayout: LinearLayout

    private lateinit var layoutChat: LinearLayout
    private lateinit var layoutMandi: LinearLayout
    private lateinit var layoutForecast: ScrollView
    private lateinit var layoutSchemes: LinearLayout
    private lateinit var layoutDisease: LinearLayout

    private lateinit var voiceOrb: CardView
    private lateinit var tvStatus: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private lateinit var spinnerDistrict: Spinner
    private lateinit var spinnerMarket: Spinner
    private lateinit var spinnerCrop: Spinner
    private lateinit var tvMandiData: TextView

    // Expandable Sections
    private var btnToggleWater: TextView? = null
    private var layoutWaterContent: LinearLayout? = null
    private var btnToggleWeather: TextView? = null
    private var layoutWeatherContent: LinearLayout? = null

    // Water & Weather inside Forecast
    private lateinit var tvWaterData: TextView
    private lateinit var tvWeatherLocation: TextView
    private lateinit var tvCurrentTemp: TextView
    private lateinit var tvWeatherCondition: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var containerHourly: LinearLayout
    private lateinit var containerDaily: LinearLayout
    private lateinit var containerRain: LinearLayout
    private lateinit var tvPrecipitationHeader: TextView
    private lateinit var tvWeatherAdvisory: TextView

    // Disease Views
    private lateinit var ivDiseasePreview: ImageView
    private lateinit var tvDiseaseResult: TextView

    // Scheme Views (Dynamic Engine)
    private lateinit var spinnerSchemeState: Spinner
    private lateinit var layoutSchemeStateSelection: LinearLayout
    private lateinit var layoutSchemeQuestionEngine: LinearLayout
    private lateinit var layoutSchemeResults: LinearLayout
    private lateinit var tvDynamicQuestionText: TextView
    private lateinit var containerDynamicInput: FrameLayout
    private lateinit var containerSchemesResult: LinearLayout
    private lateinit var tvDynamicQuestionHeader: TextView

    // Engine State variables
    private var activeSchemes = mutableListOf<AgriScheme>()
    private var userAnswers = mutableMapOf<String, Any>()
    private var currentCriteria: SchemeCriteria? = null

    // Dynamic Input References
    private var currentInputEditText: EditText? = null
    private var currentInputSpinner: Spinner? = null

    private val serverUrl = "http://10.42.56.41:8000/"

    private var isListening = false
    private var forceStopListening = true
    private var currentLanguageCode = ""
    private var currentLanguageName = ""

    private var chatHistoryList = mutableListOf<Message>()
    private var allSessions = mutableListOf<ChatSession>()
    private var currentSessionId: String = ""
    private val gson = Gson()

    private val uiTranslations = mapOf(
        "Hindi" to mapOf(
            "New Chat" to "नया चैट",
            "Tap to Speak (Tap again to stop)" to "बोलने के लिए टैप करें",
            "Type or edit prompt..." to "प्रॉम्प्ट लिखें या संपादित करें...",
            "Send" to "भेजें",
            "Live Mandi Rates" to "लाइव मंडी भाव",
            "Select District:" to "ज़िला चुनें:",
            "Select Sub-Market (Mandi):" to "उप-मंडी चुनें:",
            "Select Crop:" to "फसल चुनें:",
            "Check Prices" to "भाव जांचें",
            "Select options above to load prices." to "भाव देखने के लिए ऊपर विकल्प चुनें।",
            "Fetching live AGMARKNET rates..." to "लाइव मंडी भाव प्राप्त कर रहे हैं...",
            "Groundwater Level Prediction" to "भूजल स्तर की भविष्यवाणी",
            "Ready to analyze water tables." to "जल स्तर का विश्लेषण करने के लिए तैयार।",
            "Scan Aquifers" to "स्कैन करें",
            "Weather Prediction 7-Day Forecast" to "मौसम की भविष्यवाणी 7-दिन का पूर्वानुमान",
            "Refresh Location & Forecast" to "स्थान और मौसम ताज़ा करें",
            "AI Chat" to "AI चैट",
            "Mandi" to "मंडी",
            "Forecast" to "पूर्वानुमान",
            "Schemes" to "योजनाएं",
            "Recent Chats" to "हाल की चैट",
            "Change Language" to "भाषा बदलें",
            "Govt Scheme Matcher" to "सरकारी योजना मैच",
            "Select State/UT:" to "राज्य/केंद्र शासित प्रदेश चुनें:",
            "Start Eligibility Check" to "पात्रता जांच शुरू करें",
            "Next" to "अगला",
            "Start Over" to "फिर से शुरू करें",
            "Yes" to "हाँ",
            "No" to "नहीं",
            "Scan" to "स्कैन करें",
            "Take Photo" to "फोटो लें",
            "Gallery" to "गैलरी",
            "Upload a photo of a leaf to check for 387 diseases across 59 crops." to "59 फसलों की 387 बीमारियों की जांच के लिए पत्ते की फोटो अपलोड करें।",
            "Crop Disease Scanner" to "फसल रोग स्कैनर"
        ),
        "Punjabi" to mapOf(
            "New Chat" to "ਨਵੀਂ ਚੈਟ",
            "Tap to Speak (Tap again to stop)" to "ਬੋਲਣ ਲਈ ਟੈਪ ਕਰੋ",
            "Type or edit prompt..." to "ਪ੍ਰੋਂਪਟ ਲਿਖੋ ਜਾਂ ਸੋਧੋ...",
            "Send" to "ਭੇਜੋ",
            "Live Mandi Rates" to "ਲਾਈਵ ਮੰਡੀ ਭਾਅ",
            "Select District:" to "ਜ਼ਿਲ੍ਹਾ ਚੁਣੋ:",
            "Select Sub-Market (Mandi):" to "ਉਪ-ਮੰਡੀ ਚੁਣੋ:",
            "Select Crop:" to "ਫਸਲ ਚੁਣੋ:",
            "Check Prices" to "ਭਾਅ ਚੈੱਕ ਕਰੋ",
            "Select options above to load prices." to "ਭਾਅ ਦੇਖਣ ਲਈ ਉੱਪਰ ਵਿਕਲਪ ਚੁਣੋ।",
            "Fetching live AGMARKNET rates..." to "ਲਾਈਵ ਮੰਡੀ ਭਾਅ ਪ੍ਰਾਪਤ ਕਰ ਰਹੇ ਹਾਂ...",
            "Groundwater Level Prediction" to "ਭੂਮੀਗਤ ਪਾਣੀ ਦੇ ਪੱਧਰ ਦੀ ਭਵਿੱਖਬਾਣੀ",
            "Ready to analyze water tables." to "ਪਾਣੀ ਦੇ ਪੱਧਰ ਦਾ ਵਿਸ਼ਲੇਸ਼ਣ ਕਰਨ ਲਈ ਤਿਆਰ।",
            "Scan Aquifers" to "ਸਕੈਨ ਕਰੋ",
            "Weather Prediction 7-Day Forecast" to "ਮੌਸਮ ਦੀ ਭਵਿੱਖਬਾਣੀ 7-ਦਿਨ",
            "Refresh Location & Forecast" to "ਸਥਾਨ ਅਤੇ ਮੌਸਮ ਤਾਜ਼ਾ ਕਰੋ",
            "AI Chat" to "AI ਚੈਟ",
            "Mandi" to "ਮੰਡੀ",
            "Forecast" to "ਭਵਿੱਖਬਾਣੀ",
            "Schemes" to "ਯੋਜਨਾਵਾਂ",
            "Recent Chats" to "ਹਾਲੀਆ ਚੈਟ",
            "Change Language" to "ਭਾਸ਼ਾ ਬਦਲੋ",
            "Govt Scheme Matcher" to "ਸਰਕਾਰੀ ਯੋਜਨਾ ਮੈਚ",
            "Select State/UT:" to "ਰਾਜ/ਕੇਂਦਰ ਸ਼ਾਸਿਤ ਪ੍ਰਦੇਸ਼ ਚੁਣੋ:",
            "Start Eligibility Check" to "ਯੋਗਤਾ ਦੀ ਜਾਂਚ ਸ਼ੁਰੂ ਕਰੋ",
            "Next" to "ਅੱਗੇ",
            "Start Over" to "ਮੁੜ ਸ਼ੁਰੂ ਕਰੋ",
            "Yes" to "ਹਾਂ",
            "No" to "ਨਹੀਂ",
            "Scan" to "ਸਕੈਨ ਕਰੋ",
            "Take Photo" to "ਫੋਟੋ ਖਿੱਚੋ",
            "Gallery" to "ਗੈਲਰੀ",
            "Upload a photo of a leaf to check for 387 diseases across 59 crops." to "59 ਫਸਲਾਂ ਦੀਆਂ 387 ਬਿਮਾਰੀਆਂ ਦੀ ਜਾਂਚ ਕਰਨ ਲਈ ਪੱਤੇ ਦੀ ਫੋਟੋ ਅਪਲੋਡ ਕਰੋ।",
            "Crop Disease Scanner" to "ਫਸਲ ਰੋਗ ਸਕੈਨਰ"
        ),
        "Marathi" to mapOf(
            "New Chat" to "नवीन चॅट",
            "Tap to Speak (Tap again to stop)" to "बोलण्यासाठी टॅप करा",
            "Type or edit prompt..." to "प्रॉम्प्ट लिहा किंवा संपादित करा...",
            "Send" to "पाठवा",
            "Live Mandi Rates" to "थेट बाजार भाव",
            "Select District:" to "जिल्हा निवडा:",
            "Select Sub-Market (Mandi):" to "उप-बाजार (मंडी) निवडा:",
            "Select Crop:" to "पीक निवडा:",
            "Check Prices" to "भाव तपासा",
            "Select options above to load prices." to "भाव पाहण्यासाठी वरील पर्याय निवडा.",
            "Fetching live AGMARKNET rates..." to "थेट बाजार भाव मिळवत आहे...",
            "Groundwater Level Prediction" to "भूजल पातळीचा अंदाज",
            "Ready to analyze water tables." to "पाण्याची पातळी तपासण्यासाठी तयार.",
            "Scan Aquifers" to "स्कॅन करा",
            "Weather Prediction 7-Day Forecast" to "हवामानाचा अंदाज 7-दिवस",
            "Refresh Location & Forecast" to "ठिकाण आणि हवामान रिफ्रेश करा",
            "AI Chat" to "AI चॅट",
            "Mandi" to "बाजार",
            "Forecast" to "अंदाज",
            "Schemes" to "योजना",
            "Recent Chats" to "अलीकडील चॅट",
            "Change Language" to "भाषा बदला",
            "Govt Scheme Matcher" to "सरकारी योजना जुळणी",
            "Select State/UT:" to "राज्य/केंद्रशासित प्रदेश निवडा:",
            "Start Eligibility Check" to "पात्रता तपासणी सुरू करा",
            "Next" to "पुढील",
            "Start Over" to "पुन्हा सुरू करा",
            "Yes" to "होय",
            "No" to "नाही",
            "Scan" to "स्कॅन",
            "Take Photo" to "फोटो काढा",
            "Gallery" to "गॅलरी",
            "Upload a photo of a leaf to check for 387 diseases across 59 crops." to "59 पिकांमधील 387 रोगांची तपासणी करण्यासाठी पानाचा फोटो अपलोड करा.",
            "Crop Disease Scanner" to "पीक रोग स्कॅनर"
        ),
        "Bengali" to mapOf(
            "New Chat" to "নতুন চ্যাট",
            "Tap to Speak (Tap again to stop)" to "কথা বলতে ট্যাপ করুন",
            "Type or edit prompt..." to "প্রম্পট টাইপ বা সম্পাদনা করুন...",
            "Send" to "পাঠান",
            "Live Mandi Rates" to "লাইভ মান্ডি দর",
            "Select District:" to "জেলা নির্বাচন করুন:",
            "Select Sub-Market (Mandi):" to "সাব-মার্কেট (মান্ডি) নির্বাচন করুন:",
            "Select Crop:" to "ফসল নির্বাচন করুন:",
            "Check Prices" to "দাম চেক করুন",
            "Select options above to load prices." to "দাম দেখতে উপরের বিকল্পগুলি নির্বাচন করুন।",
            "Fetching live AGMARKNET rates..." to "লাইভ মান্ডি দর নেওয়া হচ্ছে...",
            "Groundwater Level Prediction" to "ভূগর্ভস্থ জলের স্তরের পূর্বাভাস",
            "Ready to analyze water tables." to "জলের স্তর বিশ্লেষণ করতে প্রস্তুত।",
            "Scan Aquifers" to "স্ক্যান করুন",
            "Weather Prediction 7-Day Forecast" to "আবহাওয়ার পূর্বাভাস ৭-দিন",
            "Refresh Location & Forecast" to "অবস্থান এবং আবহাওয়া রিফ্রেশ করুন",
            "AI Chat" to "AI চ্যাট",
            "Mandi" to "মান্ডি",
            "Forecast" to "পূর্বাভাস",
            "Schemes" to "স্কিম",
            "Recent Chats" to "সাম্প্রতিক চ্যাট",
            "Change Language" to "ভাষা পরিবর্তন করুন",
            "Govt Scheme Matcher" to "সরকারি স্কিম ম্যাচ",
            "Select State/UT:" to "রাজ্য/কেন্দ্রশাসিত অঞ্চল নির্বাচন করুন:",
            "Start Eligibility Check" to "যোগ্যতা যাচাই শুরু করুন",
            "Next" to "পরবর্তী",
            "Start Over" to "আবার শুরু করুন",
            "Yes" to "হ্যাঁ",
            "No" to "না",
            "Scan" to "স্ক্যান",
            "Take Photo" to "ছবি তুলুন",
            "Gallery" to "গ্যালারি",
            "Upload a photo of a leaf to check for 387 diseases across 59 crops." to "59টি ফসলের 387টি রোগের জন্য পাতার একটি ছবি আপলোড করুন।",
            "Crop Disease Scanner" to "ফসল রোগ স্ক্যানার"
        )
    )

    private fun t(key: String): String {
        if (currentLanguageName == "English" || currentLanguageName.isEmpty()) return key
        return uiTranslations[currentLanguageName]?.get(key) ?: key
    }

    private fun getLocalizedText(map: Map<String, String>?): String {
        if (map == null) return ""
        val langKey = when (currentLanguageName) {
            "Hindi" -> "hi"
            "Punjabi" -> "pa"
            "Marathi" -> "mr"
            "Bengali" -> "bn"
            else -> "en"
        }
        return map[langKey] ?: map["en"] ?: ""
    }

    private fun applyUILanguage() {
        findViewById<TextView>(R.id.btnNewChatHeader).text = "➕ " + t("New Chat")
        findViewById<TextView>(R.id.tvDrawerNewChatText).text = t("New Chat")
        findViewById<TextView>(R.id.tvDrawerRecentChatsHeader).text = t("Recent Chats")
        findViewById<TextView>(R.id.tvDrawerChangeLanguage).text = t("Change Language")

        tvStatus.text = t("Tap to Speak (Tap again to stop)")
        etMessage.hint = t("Type or edit prompt...")
        btnSend.text = t("Send")

        findViewById<TextView>(R.id.tvTabChat).text = t("AI Chat")
        findViewById<TextView>(R.id.tvTabMandi).text = t("Mandi")
        findViewById<TextView>(R.id.tvTabForecast).text = t("Forecast")
        findViewById<TextView>(R.id.tvTabSchemes).text = t("Schemes")
        findViewById<TextView>(R.id.tvTabDisease).text = t("Scan")

        findViewById<TextView>(R.id.tvMandiHeader).text = "🌾 " + t("Live Mandi Rates")
        findViewById<TextView>(R.id.tvSelectDistrict).text = t("Select District:")
        findViewById<TextView>(R.id.tvSelectMarket).text = t("Select Sub-Market (Mandi):")
        findViewById<TextView>(R.id.tvSelectCrop).text = t("Select Crop:")
        findViewById<Button>(R.id.btnRefreshMandi).text = t("Check Prices")
        tvMandiData.text = t("Select options above to load prices.")

        findViewById<TextView>(R.id.tvWaterHeader).text = "💧 " + t("Groundwater Level Prediction")
        tvWaterData.text = t("Ready to analyze water tables.")
        findViewById<Button>(R.id.btnRefreshWater).text = t("Scan Aquifers")

        findViewById<TextView>(R.id.tv7DayForecast).text = t("Weather Prediction 7-Day Forecast")
        findViewById<Button>(R.id.btnRefreshWeather).text = t("Refresh Location & Forecast")

        findViewById<TextView>(R.id.tvSchemeHeader).text = "🏛️ " + t("Govt Scheme Matcher")
        findViewById<TextView>(R.id.tvSchemeStateLabel).text = t("Select State/UT:")
        findViewById<Button>(R.id.btnStartSchemeMatch).text = t("Start Eligibility Check")
        findViewById<Button>(R.id.btnNextSchemeQuestion).text = t("Next")
        findViewById<Button>(R.id.btnRestartSchemeMatch).text = t("Start Over")

        findViewById<TextView>(R.id.tvDiseaseHeader).text = "🌿 " + t("Crop Disease Scanner")
        findViewById<Button>(R.id.btnTakePhoto).text = t("Take Photo")
        findViewById<Button>(R.id.btnUploadGallery).text = t("Gallery")
        tvDiseaseResult.text = t("Upload a photo of a leaf to check for 387 diseases across 59 crops.")
    }

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

        // Dynamic Scheme Layouts Mapping
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
            val fileName = try {
                assets.openFd("model_unquant.tflite")
                "model_unquant.tflite"
            } catch (e: Exception) {
                "model.tflite"
            }
            val fileDescriptor = assets.openFd(fileName)
            val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
            tflite = Interpreter(modelBuffer)
            tfliteLabels = assets.open("labels.txt").bufferedReader().readLines()
            Log.d("AgriKoshTFLite", "TFLite model ($fileName) loaded successfully! Labels: $tfliteLabels")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AgriKoshTFLite", "Error loading TFLite model: ${e.message}", e)
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

    private fun setupButtons() {
        findViewById<TextView>(R.id.btnOpenMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.btnNewChatHeader).setOnClickListener { createNewSession() }
        findViewById<LinearLayout>(R.id.btnDrawerNewChat).setOnClickListener {
            createNewSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<LinearLayout>(R.id.btnDrawerChangeLanguage).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mainAppLayout.visibility = View.GONE
            bottomNavLayout.visibility = View.GONE
            languageSelectorLayout.visibility = View.VISIBLE
            findViewById<TextView>(R.id.btnCloseLanguage).visibility = View.VISIBLE
        }

        voiceOrb.setOnClickListener {
            if (!isListening) { forceStopListening = false; startListening() } else { forceStopListening = true; stopListening() }
        }
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                addChatBubble(text, isUser = true)
                val newMsg = Message("user", text)
                chatHistoryList.add(newMsg)

                val session = allSessions.find { it.id == currentSessionId }
                if (session != null) {
                    if (session.messages.isEmpty()) {
                        session.title = text.take(22) + if (text.length > 22) "..." else ""
                    }
                    session.messages.add(newMsg)
                    saveSessionsToPrefs()
                }

                sendToKisanSLM()
                etMessage.text.clear()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0); etMessage.clearFocus()
            }
        }
        findViewById<Button>(R.id.btnRefreshMandi).setOnClickListener { fetchMandiData() }

        findViewById<Button>(R.id.btnRefreshWater).setOnClickListener {
            layoutWaterContent?.visibility = View.VISIBLE
            btnToggleWater?.text = "▲"
            checkGroundwaterLevels()
        }
        findViewById<Button>(R.id.btnRefreshWeather).setOnClickListener {
            layoutWeatherContent?.visibility = View.VISIBLE
            btnToggleWeather?.text = "▲"
            checkLocationAndFetchWeather()
        }

        // Dynamic Engine Scheme Logic Buttons
        findViewById<Button>(R.id.btnStartSchemeMatch).setOnClickListener { startSchemeEngine() }
        findViewById<Button>(R.id.btnNextSchemeQuestion).setOnClickListener { processUserAnswer() }
        findViewById<Button>(R.id.btnRestartSchemeMatch).setOnClickListener {
            layoutSchemeResults.visibility = View.GONE
            layoutSchemeStateSelection.visibility = View.VISIBLE
        }

        // Disease Prediction Buttons
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener { takePictureLauncher.launch(null) }
        findViewById<Button>(R.id.btnUploadGallery).setOnClickListener { pickGalleryLauncher.launch("image/*") }
    }

    private fun processDiseaseImage(bitmap: Bitmap) {
        tvDiseaseResult.text = "Checking image locally with TFLite..."

        if (tflite == null || tfliteLabels.isEmpty()) {
            Toast.makeText(this, "Local TFLite model not initialized. Loading model...", Toast.LENGTH_SHORT).show()
            loadTFLiteModel()
        }

        if (tflite != null && tfliteLabels.isNotEmpty()) {
            try {
                val inputTensor = tflite!!.getInputTensor(0)
                val outputTensor = tflite!!.getOutputTensor(0)

                val inputDataType = inputTensor.dataType()
                val inputShape = inputTensor.shape()

                val imageWidth = if (inputShape.size >= 3) inputShape[2] else 224
                val imageHeight = if (inputShape.size >= 3) inputShape[1] else 224

                val resized = Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, true)

                val isFloat = inputDataType == DataType.FLOAT32
                val bytesPerChannel = if (isFloat) 4 else 1
                val inputBuffer = ByteBuffer.allocateDirect(1 * imageHeight * imageWidth * 3 * bytesPerChannel)
                inputBuffer.order(ByteOrder.nativeOrder())

                val intValues = IntArray(imageWidth * imageHeight)
                resized.getPixels(intValues, 0, imageWidth, 0, 0, imageWidth, imageHeight)

                var pixel = 0
                for (i in 0 until imageHeight) {
                    for (j in 0 until imageWidth) {
                        val valInt = intValues[pixel++]
                        val r = (valInt shr 16) and 0xFF
                        val g = (valInt shr 8) and 0xFF
                        val b = valInt and 0xFF

                        if (isFloat) {
                            // Standard Teachable Machine MobileNet normalization: (val / 127.5f) - 1.0f
                            inputBuffer.putFloat((r / 127.5f) - 1.0f)
                            inputBuffer.putFloat((g / 127.5f) - 1.0f)
                            inputBuffer.putFloat((b / 127.5f) - 1.0f)
                        } else {
                            inputBuffer.put(r.toByte())
                            inputBuffer.put(g.toByte())
                            inputBuffer.put(b.toByte())
                        }
                    }
                }

                val numClasses = tfliteLabels.size
                val outputIsFloat = outputTensor.dataType() == DataType.FLOAT32

                val leafIndex = tfliteLabels.indexOfFirst {
                    val clean = it.lowercase()
                    clean.contains("leaf") && !clean.contains("not") && !clean.contains("non")
                }.coerceAtLeast(0)

                val notLeafIndex = tfliteLabels.indexOfFirst {
                    val clean = it.lowercase()
                    clean.contains("not") || clean.contains("non") || clean.contains("other") || clean.contains("background")
                }.let { if (it >= 0) it else (if (leafIndex == 0) 1 else 0) }

                val leafProb: Float
                val notLeafProb: Float

                if (outputIsFloat) {
                    val outputArray = Array(1) { FloatArray(numClasses) }
                    tflite!!.run(inputBuffer, outputArray)
                    leafProb = outputArray[0][leafIndex]
                    notLeafProb = outputArray[0][notLeafIndex]
                } else {
                    val outputArray = Array(1) { ByteArray(numClasses) }
                    tflite!!.run(inputBuffer, outputArray)
                    leafProb = (outputArray[0][leafIndex].toInt() and 0xFF) / 255.0f
                    notLeafProb = (outputArray[0][notLeafIndex].toInt() and 0xFF) / 255.0f
                }

                val leafPercent = (leafProb * 100).toInt()
                val notLeafPercent = (notLeafProb * 100).toInt()

                Log.d("AgriKoshTFLite", "Gatekeeper Result -> Leaf: $leafPercent%, Not Leaf: $notLeafPercent% (leafIdx=$leafIndex, notLeafIdx=$notLeafIndex)")

                // Strict Threshold: Require leaf probability to be at least 75% AND higher than not_leaf
                if (leafProb < 0.75f || leafProb <= notLeafProb) {
                    tvDiseaseResult.text = "⚠️ Image Rejected (Local Gatekeeper)\n\n" +
                            "Detected: Not a Leaf (${notLeafPercent}% confidence, Leaf match: ${leafPercent}%).\n\n" +
                            "Please upload or capture a clear photo of a crop leaf to scan for diseases."
                    return
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("AgriKoshTFLite", "Gatekeeper error: ${e.message}", e)
                tvDiseaseResult.text = "⚠️ Local gatekeeper error: ${e.localizedMessage}\n\nPlease ensure a valid leaf photo is selected."
                return
            }
        } else {
            tvDiseaseResult.text = "⚠️ Local gatekeeper model not loaded.\n\nPlease ensure model.tflite is present."
            return
        }

        // Passed Gatekeeper (or if TFLite missing), Upload to Fast API Server
        tvDiseaseResult.text = "Uploading to India Multi-Crop Model..."

        try {
            val file = File(cacheDir, "disease_capture.jpg")
            val os = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)
            os.close()

            val reqFile = RequestBody.create(MediaType.parse("image/jpeg"), file)
            val body = MultipartBody.Part.createFormData("file", file.name, reqFile)

            apiService.predictDisease(body).enqueue(object : Callback<DiseaseResponse> {
                override fun onResponse(call: Call<DiseaseResponse>, response: Response<DiseaseResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        if (data.status == "success") {
                            var res = "📌 Prediction: ${data.prediction}\n\nTop Matches:\n"
                            data.top_classes?.forEach {
                                res += "- ${it.label} (${(it.probability * 100).toInt()}%)\n"
                            }
                            res += "\n💡 Advisory: ${data.advisory}"
                            tvDiseaseResult.text = res
                            if(::tts.isInitialized) {
                                tts.speak(data.advisory, TextToSpeech.QUEUE_FLUSH, null, "")
                            }
                        } else {
                            tvDiseaseResult.text = "Server Error: ${data.message}"
                        }
                    } else {
                        tvDiseaseResult.text = "Failed to analyze image on server."
                    }
                }
                override fun onFailure(call: Call<DiseaseResponse>, t: Throwable) {
                    tvDiseaseResult.text = "Connection Failed. Is the server running?"
                }
            })
        } catch (e: Exception) {
            tvDiseaseResult.text = "Error saving image: ${e.localizedMessage}"
        }
    }

    private fun startSchemeEngine() {
        val selectedState = spinnerSchemeState.selectedItem.toString()
        userAnswers.clear()
        activeSchemes.clear()

        try {
            val inputStream = resources.openRawResource(R.raw.agri_schemes)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<AgriScheme>>() {}.type
            val allSchemes: List<AgriScheme> = gson.fromJson(reader, type)
            reader.close()

            // Step 1: Filter Central and Selected State exactly matching the new JSON
            for (scheme in allSchemes) {
                if (scheme.state == "All" || scheme.state == selectedState) {
                    activeSchemes.add(scheme)
                }
            }

            if (activeSchemes.isEmpty()) {
                Toast.makeText(this, "No schemes available for this location yet.", Toast.LENGTH_SHORT).show()
                return
            }

            // Move to Question Engine UI
            layoutSchemeStateSelection.visibility = View.GONE
            layoutSchemeQuestionEngine.visibility = View.VISIBLE

            askNextQuestion()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading scheme data.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNextQuestion() {
        currentCriteria = null
        currentInputEditText = null
        currentInputSpinner = null

        // Loop through active schemes and their criteria to find the first unanswered question
        for (scheme in activeSchemes) {
            for (criteria in scheme.criteria) {
                if (!userAnswers.containsKey(criteria.questionId)) {
                    currentCriteria = criteria
                    break
                }
            }
            if (currentCriteria != null) break
        }

        // If no more unanswered questions, the user passed all remaining schemes
        if (currentCriteria == null) {
            showFinalEligibleSchemes()
            return
        }

        // Setup the UI for the newly found question
        val c = currentCriteria!!
        tvDynamicQuestionHeader.text = "Evaluating ${activeSchemes.size} Potential Schemes"
        tvDynamicQuestionText.text = getLocalizedText(c.questionText)

        containerDynamicInput.removeAllViews()

        when (c.inputType) {
            "boolean" -> {
                currentInputSpinner = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf(t("Yes"), t("No")))
                    background = ContextCompat.getDrawable(this@MainActivity, android.R.color.transparent)
                    val bg = GradientDrawable()
                    bg.setColor(Color.parseColor("#2E2836"))
                    bg.cornerRadius = 12f
                    background = bg
                    setPadding(16, 16, 16, 16)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt())
                }
                containerDynamicInput.addView(currentInputSpinner)
            }
            "select" -> {
                val opts = c.options ?: listOf()
                val labels = opts.map { getLocalizedText(it.label) }.toTypedArray()
                currentInputSpinner = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
                    background = ContextCompat.getDrawable(this@MainActivity, android.R.color.transparent)
                    val bg = GradientDrawable()
                    bg.setColor(Color.parseColor("#2E2836"))
                    bg.cornerRadius = 12f
                    background = bg
                    setPadding(16, 16, 16, 16)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt())
                }
                containerDynamicInput.addView(currentInputSpinner)
            }
            "number" -> {
                currentInputEditText = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    hint = "0.0"
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.parseColor("#757575"))
                    val bg = GradientDrawable()
                    bg.setColor(Color.parseColor("#2E2836"))
                    bg.cornerRadius = 12f
                    background = bg
                    setPadding(32, 0, 32, 0)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt())
                }
                containerDynamicInput.addView(currentInputEditText)
            }
        }
    }

    private fun processUserAnswer() {
        val c = currentCriteria ?: return

        // Step 1: Read UI to get the user's answer
        var savedAnswer: Any? = null
        if (c.inputType == "boolean" && currentInputSpinner != null) {
            savedAnswer = currentInputSpinner!!.selectedItemPosition == 0 // true if "Yes" (index 0)
        } else if (c.inputType == "select" && currentInputSpinner != null) {
            val selectedIndex = currentInputSpinner!!.selectedItemPosition
            if (c.options != null && selectedIndex >= 0 && selectedIndex < c.options.size) {
                savedAnswer = c.options[selectedIndex].value
            }
        } else if (c.inputType == "number" && currentInputEditText != null) {
            val textAns = currentInputEditText!!.text.toString()
            if (textAns.isEmpty()) {
                Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
                return
            }
            savedAnswer = textAns.toDoubleOrNull()
            if (savedAnswer == null) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (savedAnswer != null) {
            // Close Keyboard if it's a number input
            if (c.inputType == "number" && currentInputEditText != null) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(currentInputEditText!!.windowToken, 0)
            }

            // Save for de-duplication
            userAnswers[c.questionId] = savedAnswer

            // Step 2: Instantly eliminate schemes using this new answer
            evaluateActiveSchemes()

            // Step 3: Pull up the next question
            askNextQuestion()
        }
    }

    private fun evaluateActiveSchemes() {
        val iterator = activeSchemes.iterator()
        while (iterator.hasNext()) {
            val scheme = iterator.next()
            var isEligible = true

            for (criteria in scheme.criteria) {
                // If the user answered this criteria's standard question ID
                if (userAnswers.containsKey(criteria.questionId)) {
                    val userAnswer = userAnswers[criteria.questionId]!!
                    if (!evaluateSingleCondition(userAnswer, criteria)) {
                        isEligible = false
                        break
                    }
                }
            }

            if (!isEligible) {
                iterator.remove() // Eliminate instantly
            }
        }
    }

    private fun evaluateSingleCondition(userAnswer: Any, criteria: SchemeCriteria): Boolean {
        try {
            val target = criteria.targetValue ?: return false
            when (criteria.operator) {
                "eq" -> {
                    if (criteria.inputType == "boolean") {
                        return (userAnswer as Boolean) == target.asBoolean
                    } else if (criteria.inputType == "select") {
                        return (userAnswer as String).equals(target.asString, ignoreCase = true)
                    } else if (criteria.inputType == "number") {
                        return (userAnswer as Double) == target.asDouble
                    }
                }
                "gte" -> {
                    return (userAnswer as Double) >= target.asDouble
                }
                "lte" -> {
                    return (userAnswer as Double) <= target.asDouble
                }
                "in" -> {
                    val ans = userAnswer as String
                    val array = target.asJsonArray
                    for (i in 0 until array.size()) {
                        if (ans.equals(array[i].asString, ignoreCase = true)) return true
                    }
                    return false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false // Fail securely
        }
        return false
    }

    private fun showFinalEligibleSchemes() {
        layoutSchemeQuestionEngine.visibility = View.GONE
        layoutSchemeResults.visibility = View.VISIBLE
        containerSchemesResult.removeAllViews()

        if (activeSchemes.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Based on your answers, you are not directly eligible for the listed schemes at this time."
                setTextColor(Color.WHITE)
                textSize = 16f
            }
            containerSchemesResult.addView(tvEmpty)
        } else {
            val tvMatchCount = TextView(this).apply {
                text = "Congratulations! You are eligible for ${activeSchemes.size} scheme(s):"
                setTextColor(Color.parseColor("#00E676"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            containerSchemesResult.addView(tvMatchCount)

            for (scheme in activeSchemes) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val bg = GradientDrawable()
                    bg.setColor(Color.parseColor("#2E2836"))
                    bg.cornerRadius = 16f
                    background = bg
                    setPadding(32, 32, 32, 32)

                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.setMargins(0, 0, 0, 24)
                    layoutParams = params
                }

                val title = TextView(this).apply {
                    text = "📌 ${getLocalizedText(scheme.name)}"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setPadding(0, 0, 0, 12)
                }

                val govtLevel = TextView(this).apply {
                    text = "Level: ${scheme.level} Government"
                    setTextColor(Color.parseColor("#90CAF9"))
                    textSize = 14f
                    setPadding(0, 0, 0, 12)
                }

                val benefit = TextView(this).apply {
                    text = "Benefit: ${getLocalizedText(scheme.benefit)}"
                    setTextColor(Color.parseColor("#E0E0E0"))
                    textSize = 15f
                    setLineSpacing(4f, 1f)
                }

                card.addView(title)
                card.addView(govtLevel)
                card.addView(benefit)
                containerSchemesResult.addView(card)
            }
        }
    }

    private fun checkLocationAndFetchWeather() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("Your GPS is turned off. AgriKosh needs your location to fetch accurate local weather. Please enable it in Settings.")
                .setPositiveButton("Open Settings") { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("Cancel") { dialog, _ -> tvWeatherAdvisory.text = "GPS required for accurate weather."; dialog.cancel() }
                .show()
            return
        }

        tvWeatherAdvisory.text = "Acquiring GPS signal..."

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lastKnown: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                if (lastKnown != null) {
                    processLocationAndFetchWeather(lastKnown.latitude, lastKnown.longitude)
                    return
                }

                var locationFetched = false
                val handler = android.os.Handler(android.os.Looper.getMainLooper())

                val locationListener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        if (!locationFetched) {
                            locationFetched = true
                            try { locationManager.removeUpdates(this) } catch (e: Exception) {}
                            processLocationAndFetchWeather(loc.latitude, loc.longitude)
                        }
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                val timeoutRunnable = Runnable {
                    if (!locationFetched) {
                        locationFetched = true
                        try { locationManager.removeUpdates(locationListener) } catch (e: Exception) {}
                        val prefs = getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE)
                        executeWeatherApi(prefs.getFloat("last_lat", 30.67995f).toDouble(), prefs.getFloat("last_lon", 76.72211f).toDouble(), prefs.getString("last_city", "Mohali, Punjab") ?: "Mohali, Punjab")
                    }
                }

                handler.postDelayed(timeoutRunnable, 3000)
                if (isNetworkEnabled) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
                if (isGpsEnabled) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val prefs = getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE)
            executeWeatherApi(prefs.getFloat("last_lat", 30.67995f).toDouble(), prefs.getFloat("last_lon", 76.72211f).toDouble(), prefs.getString("last_city", "Mohali, Punjab") ?: "Mohali, Punjab")
        }
    }

    private fun processLocationAndFetchWeather(lat: Double, lon: Double) {
        var cityName = "Unknown Location"
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val town = addresses[0].locality ?: addresses[0].subAdminArea ?: "Unknown"
                val state = addresses[0].adminArea ?: ""
                cityName = if (state.isNotEmpty()) "$town, $state" else town
            }
        } catch (e: Exception) { e.printStackTrace() }

        getSharedPreferences("AgriKoshPrefs", Context.MODE_PRIVATE).edit().putFloat("last_lat", lat.toFloat()).putFloat("last_lon", lon.toFloat()).putString("last_city", cityName).apply()
        executeWeatherApi(lat, lon, cityName)
    }

    private fun executeWeatherApi(lat: Double, lon: Double, city: String) {
        tvWeatherLocation.text = "📍 $city"
        tvWeatherAdvisory.text = "Fetching live weather..."

        apiService.getWeather(lat, lon, city, currentLanguageName).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                val data = response.body()
                if (data != null) {
                    tvWeatherLocation.text = "📍 " + data.location_name
                    tvCurrentTemp.text = data.current_temp
                    tvWeatherCondition.text = data.condition_text
                    tvFeelsLike.text = data.feels_like

                    val rawHeader = data.precipitation_header
                    val cleanHeader = rawHeader.replace(Regex("[☁️🌨️🌧️⛈️]"), "").trim()
                    tvPrecipitationHeader.text = "💧 $cleanHeader"

                    tvWeatherAdvisory.text = "💡 ${data.advisory}"

                    populateHourlyTray(containerHourly, data.hourly)
                    populateDailyTray(containerDaily, data.daily)
                    populatePrecipitationTray(containerRain, data.hourly)
                } else { tvWeatherAdvisory.text = "Failed to receive weather payload." }
            }
            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                tvWeatherAdvisory.text = "Connection Failed. Check Server Connection."
            }
        })
    }

    private fun populateHourlyTray(container: LinearLayout, items: List<HourlyItem>) {
        container.removeAllViews()
        for (item in items) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 12, 4, 12); minimumWidth = (60 * resources.displayMetrics.density).toInt(); gravity = Gravity.CENTER }
            val tvTime = TextView(this).apply { text = item.time; setTextColor(Color.parseColor("#AFAFAF")); textSize = 13f; gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            val tvIcon = TextView(this).apply { text = item.icon; textSize = 22f; setPadding(0, 6, 0, 6); gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            val tvTemp = TextView(this).apply { text = item.temp; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            cell.addView(tvTime); cell.addView(tvIcon); cell.addView(tvTemp); container.addView(cell)
        }
    }

    private fun populateDailyTray(container: LinearLayout, items: List<DailyItem>) {
        container.removeAllViews()
        for (item in items) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); minimumWidth = (80 * resources.displayMetrics.density).toInt(); gravity = Gravity.CENTER; val bg = GradientDrawable(); bg.setColor(Color.parseColor("#292436")); bg.cornerRadius = 16f; background = bg; val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.setMargins(8, 0, 8, 0); layoutParams = params }
            val tvDay = TextView(this).apply { text = item.day; setTextColor(Color.parseColor("#E0E0E0")); textSize = 14f; gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            val tvIcon = TextView(this).apply { text = item.icon; textSize = 24f; setPadding(0, 8, 0, 8); gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            val tvRange = TextView(this).apply { text = item.temp_range; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            cell.addView(tvDay); cell.addView(tvIcon); cell.addView(tvRange); container.addView(cell)
        }
    }

    private fun populatePrecipitationTray(container: LinearLayout, items: List<HourlyItem>) {
        container.removeAllViews()
        val maxBarHeightPx = (70 * resources.displayMetrics.density).toInt()
        for (item in items) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 0, 4, 0); minimumWidth = (60 * resources.displayMetrics.density).toInt(); gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM }
            val tvProb = TextView(this).apply { text = item.rain_prob; setTextColor(Color.parseColor("#90CAF9")); textSize = 12f; setPadding(0, 0, 0, 6); gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            val barHeight = ((item.rain_val / 100f) * maxBarHeightPx).toInt().coerceAtLeast(8)
            val barView = View(this).apply { val params = LinearLayout.LayoutParams((18 * resources.displayMetrics.density).toInt(), barHeight); params.setMargins(0, 4, 0, 8); params.gravity = Gravity.CENTER_HORIZONTAL; layoutParams = params; val bg = GradientDrawable(); bg.setColor(if (item.rain_val > 25) Color.parseColor("#42A5F5") else Color.parseColor("#5C6BC0")); bg.cornerRadius = 8f; background = bg }
            val tvTime = TextView(this).apply { text = item.time; setTextColor(Color.parseColor("#AFAFAF")); textSize = 13f; gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER }
            cell.addView(tvProb); cell.addView(barView); cell.addView(tvTime); container.addView(cell)
        }
    }

    private fun fetchMandiData() {
        val selectedDistrict = spinnerDistrict.selectedItem?.toString() ?: "Ludhiana"
        val selectedMarket = spinnerMarket.selectedItem?.toString() ?: "Ludhiana"
        val selectedCrop = spinnerCrop.selectedItem?.toString() ?: "Wheat"

        tvMandiData.text = t("Fetching live AGMARKNET rates...")

        apiService.getMandiPrices(selectedDistrict, selectedMarket, selectedCrop, currentLanguageName).enqueue(object : Callback<MandiResponse> {
            override fun onResponse(call: Call<MandiResponse>, response: Response<MandiResponse>) {
                val data = response.body()
                if (data != null) {
                    var text = "📍 ${t("Mandi")}: ${data.market} ($selectedDistrict)\n📅 ${data.date}\n\n"
                    if (data.commodities.isEmpty()) {
                        text += "No records arrived for $selectedCrop today."
                    } else {
                        data.commodities.forEach { c -> text += "${c.name}: ${c.price} (${c.trend})\n" }
                    }
                    text += "\n💡 ${t("AI Chat")}: ${data.advisory}"
                    tvMandiData.text = text
                    tts.speak(data.advisory, TextToSpeech.QUEUE_FLUSH, null, "")
                } else { tvMandiData.text = "Failed to load mandi data." }
            }
            override fun onFailure(call: Call<MandiResponse>, t: Throwable) { tvMandiData.text = "Connection Failed. Check Server Connection." }
        })
    }

    private fun checkGroundwaterLevels() {
        tvWaterData.text = "Scanning groundwater tables..."
        apiService.checkGroundwater(currentLanguageName).enqueue(object : Callback<WaterResponse> {
            override fun onResponse(call: Call<WaterResponse>, response: Response<WaterResponse>) {
                val data = response.body()
                if (data != null) {
                    tvWaterData.text = "📍 ${data.location}\n💧 Current Depth: ${data.current_depth_m}m\n🔮 Forecast: ${data.predicted_depth_m}m\n\n⚠️ ${data.advisory}"
                    tts.speak(data.advisory, TextToSpeech.QUEUE_FLUSH, null, "")
                } else { tvWaterData.text = "Failed to analyze groundwater." }
            }
            override fun onFailure(call: Call<WaterResponse>, t: Throwable) { tvWaterData.text = "Connection Failed. Check Server Connection." }
        })
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
}
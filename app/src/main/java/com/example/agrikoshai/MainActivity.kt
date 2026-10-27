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

interface ApiService {
    @retrofit2.http.POST("/chat") fun chatWithKisan(@retrofit2.http.Body request: ChatRequest): Call<ChatResponse>
    @retrofit2.http.GET("/groundwater") fun checkGroundwater(@retrofit2.http.Query("lang") lang: String): Call<WaterResponse>
    @retrofit2.http.GET("/mandi") fun getMandiPrices(@retrofit2.http.Query("district") district: String, @retrofit2.http.Query("market") market: String, @retrofit2.http.Query("commodity") commodity: String, @retrofit2.http.Query("lang") lang: String): Call<MandiResponse>
    @retrofit2.http.GET("/weather") fun getWeather(@retrofit2.http.Query("lat") lat: Double, @retrofit2.http.Query("lon") lon: Double, @retrofit2.http.Query("city") city: String, @retrofit2.http.Query("lang") lang: String): Call<WeatherResponse>
    @retrofit2.http.Multipart @retrofit2.http.POST("/predict-disease") fun predictDisease(@retrofit2.http.Part file: MultipartBody.Part): Call<DiseaseResponse>
}
}
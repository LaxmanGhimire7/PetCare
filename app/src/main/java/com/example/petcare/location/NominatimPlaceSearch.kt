package com.example.petcare.location

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

sealed interface PlaceSearchResult {
    data class Found(val latitude: Double, val longitude: Double, val label: String) : PlaceSearchResult
    data object NotFound : PlaceSearchResult
    data object Unavailable : PlaceSearchResult
}

/** Explicit, low-volume college-demo search. Never call this for autocomplete or bulk queries. */
object NominatimPlaceSearch {
    // The public service can be disabled at runtime via the Places Search long-press dialog.
    // Replace it with a managed/self-hosted service before production distribution.
    private const val SEARCH_HOST = "nominatim.openstreetmap.org"
    private const val USER_AGENT = "PetCare/2.0 (Android; com.example.petcare; college project)"
    private const val PREFS = "petcare_place_search"
    private const val USE_PUBLIC_SERVICE = "use_public_osm_search"
    private const val TAG = "PetCarePlaceSearch"
    private val requestMutex = Mutex()
    private val recentResults = LinkedHashMap<String, PlaceSearchResult>()
    private var lastRequestAtMs = 0L
    @Volatile private var httpClient: OkHttpClient? = null

    fun usesPublicService(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(USE_PUBLIC_SERVICE, true)

    fun setUsesPublicService(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(USE_PUBLIC_SERVICE, enabled).apply()
    }

    suspend fun search(context: Context, query: String, near: Pair<Double, Double>?): PlaceSearchResult {
        val url = searchUrl(query, near)
        val key = url.toString()
        requestMutex.lock()
        try {
            recentResults[key]?.let { return it }
            val waitMs = 1_000L - (SystemClock.elapsedRealtime() - lastRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastRequestAtMs = SystemClock.elapsedRealtime()
            val request = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Language", Locale.getDefault().toLanguageTag())
                .build()
            val result = try {
                execute(context, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OpenStreetMap search could not be started", e)
                PlaceSearchResult.Unavailable
            }
            if (result != PlaceSearchResult.Unavailable) {
                if (recentResults.size >= 64) recentResults.remove(recentResults.keys.first())
                recentResults[key] = result
            }
            return result
        } finally {
            requestMutex.unlock()
        }
    }

    internal fun searchUrl(query: String, near: Pair<Double, Double>?): HttpUrl {
        val builder = HttpUrl.Builder().scheme("https").host(SEARCH_HOST)
            .addPathSegment("search")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "1")
        near?.takeIf { it.first in -90.0..90.0 && it.second in -180.0..180.0 }?.let {
            val latitude = (it.first * 10).roundToInt() / 10.0
            val longitude = (it.second * 10).roundToInt() / 10.0
            val west = (longitude - 0.75).coerceAtLeast(-180.0)
            val east = (longitude + 0.75).coerceAtMost(180.0)
            val north = (latitude + 0.75).coerceAtMost(90.0)
            val south = (latitude - 0.75).coerceAtLeast(-90.0)
            builder.addQueryParameter("viewbox", "$west,$north,$east,$south")
        }
        return builder.build()
    }

    private suspend fun execute(context: Context, request: Request): PlaceSearchResult =
        suspendCancellableCoroutine { continuation ->
            val call = client(context).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    Log.w(TAG, "OpenStreetMap search request failed", e)
                    continuation.resume(PlaceSearchResult.Unavailable)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) {
                            Log.w(TAG, "OpenStreetMap search returned HTTP ${it.code}")
                            PlaceSearchResult.Unavailable
                        } else try {
                            val results = JSONArray(it.body?.string() ?: "")
                            if (results.length() == 0) PlaceSearchResult.NotFound
                            else {
                                val first = results.getJSONObject(0)
                                val latitude = first.optString("lat").toDoubleOrNull()
                                val longitude = first.optString("lon").toDoubleOrNull()
                                if (latitude == null || longitude == null ||
                                    latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                                    Log.w(TAG, "OpenStreetMap search returned invalid coordinates")
                                    PlaceSearchResult.Unavailable
                                } else PlaceSearchResult.Found(latitude, longitude,
                                    first.optString("name").ifBlank {
                                        first.optString("display_name").substringBefore(',')
                                    })
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "OpenStreetMap search response could not be read", e)
                            PlaceSearchResult.Unavailable
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    @Synchronized
    private fun client(context: Context): OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "osm-place-search-cache"),
            10L * 1024 * 1024))
        .callTimeout(10, TimeUnit.SECONDS)
        .build().also { httpClient = it }
}

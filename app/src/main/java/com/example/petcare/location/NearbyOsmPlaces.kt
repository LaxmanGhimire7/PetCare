package com.example.petcare.location

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.petcare.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class NearbyCategory { VET, GROOMING, PARK }

data class NearbyOsmPlace(
    val osmKey: String,
    val category: NearbyCategory,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

sealed interface NearbyLoadResult {
    data class Success(val places: List<NearbyOsmPlace>, val incomplete: Boolean = false) : NearbyLoadResult
    data object Unavailable : NearbyLoadResult
}

/** Small, explicit-tap OSM lookup for the Places chips; never scan the map automatically. */
object NearbyOsmPlaces {
    // Public Overpass is only suitable for this low-volume college demo. Use a managed or
    // self-hosted service before distributing the app broadly.
    private const val PRIMARY_ENDPOINT = "https://overpass-api.de/api/interpreter"
    // FOSSGIS documents direct-server access as a workaround when one of its two servers fails.
    private const val FALLBACK_ENDPOINT = "https://lambert.openstreetmap.de/api/interpreter"
    private const val USER_AGENT = "PetCare/2.0 (Android; com.example.petcare; college project)"
    private const val RADIUS_METRES = 5_000
    private const val CACHE_MS = 10 * 60 * 1_000L
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private const val TAG = "PetCareNearbyPlaces"
    private val requestMutex = Mutex()
    private val cache = LinkedHashMap<String, Pair<Long, List<NearbyOsmPlace>>>()
    private var lastRequestAtMs = 0L
    private var lastFailureAtMs = 0L
    private var fallbackUntilMs = 0L
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    private sealed interface FetchResult {
        data class Success(val places: List<NearbyOsmPlace>) : FetchResult
        data object RetryableFailure : FetchResult
        data object NoRetryFailure : FetchResult
    }

    suspend fun load(context: Context, latitude: Double, longitude: Double,
                     category: NearbyCategory): NearbyLoadResult {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)
            return NearbyLoadResult.Unavailable
        // Quantizing the centre reuses one response for small map movements.
        val centreLat = (latitude * 50).roundToInt() / 50.0
        val centreLon = (longitude * 50).roundToInt() / 50.0
        val key = "$centreLat,$centreLon,$category"
        requestMutex.lock()
        try {
            cache[key]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_MS }?.let {
                return NearbyLoadResult.Success(it.second)
            }
            if (lastFailureAtMs != 0L &&
                SystemClock.elapsedRealtime() - lastFailureAtMs < FAILURE_COOLDOWN_MS) {
                return NearbyLoadResult.Unavailable
            }
            val result = try {
                val placeQuery = query(centreLat, centreLon, category)
                val useFallback = SystemClock.elapsedRealtime() < fallbackUntilMs
                val endpoint = if (useFallback) FALLBACK_ENDPOINT else PRIMARY_ENDPOINT
                var fetched = fetch(context, endpoint, placeQuery, centreLat, centreLon, category)
                if (!useFallback && fetched == FetchResult.RetryableFailure) {
                    fetched = fetch(context, FALLBACK_ENDPOINT, placeQuery,
                        centreLat, centreLon, category)
                    if (fetched is FetchResult.Success)
                        fallbackUntilMs = SystemClock.elapsedRealtime() + CACHE_MS
                }
                if (fetched is FetchResult.Success) NearbyLoadResult.Success(fetched.places)
                    else NearbyLoadResult.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Nearby OSM lookup could not be started", e)
                NearbyLoadResult.Unavailable
            }
            if (result is NearbyLoadResult.Success) {
                lastFailureAtMs = 0L
                if (cache.size >= 12) cache.remove(cache.keys.first())
                cache[key] = SystemClock.elapsedRealtime() to result.places
            } else lastFailureAtMs = SystemClock.elapsedRealtime()
            return result
        } finally {
            requestMutex.unlock()
        }
    }

    internal fun query(latitude: Double, longitude: Double, category: NearbyCategory): String {
        val selector = when (category) {
            NearbyCategory.VET -> "[\"amenity\"=\"veterinary\"]"
            NearbyCategory.GROOMING -> "[\"shop\"~\"^(pet_grooming|dog_grooming)${'$'}\"]"
            NearbyCategory.PARK -> "[\"leisure\"~\"^(park|dog_park)${'$'}\"]"
        }
        return """[out:json][timeout:15];(
            node$selector(around:$RADIUS_METRES,$latitude,$longitude);
            way$selector(around:$RADIUS_METRES,$latitude,$longitude);
        );out center;""".trimIndent()
    }

    private suspend fun fetch(context: Context, endpoint: String, query: String,
                              latitude: Double, longitude: Double,
                              category: NearbyCategory): FetchResult {
        val waitMs = 2_000L - (SystemClock.elapsedRealtime() - lastRequestAtMs)
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = SystemClock.elapsedRealtime()
        val request = Request.Builder().url(endpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()
        return execute(context, request, latitude, longitude, category)
    }

    private suspend fun execute(context: Context, request: Request, latitude: Double,
                                longitude: Double, category: NearbyCategory): FetchResult =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    Log.w(TAG, "Nearby OSM lookup failed", e)
                    continuation.resume(FetchResult.RetryableFailure)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) {
                            Log.w(TAG, "Nearby OSM lookup returned HTTP ${it.code}")
                            if (it.code >= 500) FetchResult.RetryableFailure
                                else FetchResult.NoRetryFailure
                        } else try {
                            val elements = JSONObject(it.body?.string() ?: "")
                                .getJSONArray("elements")
                            val places = buildList {
                                for (index in 0 until elements.length()) {
                                    val element = elements.optJSONObject(index) ?: continue
                                    parsePlace(context, element)?.let(::add)
                                }
                            }
                            val closest = places.filter { it.category == category }
                                .distinctBy(NearbyOsmPlace::osmKey)
                                .sortedBy { place -> PlaceLocation.distanceKm(latitude, longitude,
                                    place.latitude, place.longitude) }
                                .take(30)
                            Log.i(TAG, "Nearby ${category.name} lookup returned ${closest.size} places")
                            FetchResult.Success(closest)
                        } catch (e: Exception) {
                            Log.w(TAG, "Nearby OSM response could not be read", e)
                            // Overpass can return a busy/runtime-error HTML page with HTTP 200.
                            // Retry once on the other FOSSGIS server in that case.
                            FetchResult.RetryableFailure
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun parsePlace(context: Context, element: JSONObject): NearbyOsmPlace? {
        val tags = element.optJSONObject("tags") ?: return null
        val category = when {
            tags.optString("amenity") == "veterinary" -> NearbyCategory.VET
            tags.optString("shop") in setOf("pet_grooming", "dog_grooming") -> NearbyCategory.GROOMING
            tags.optString("leisure") in setOf("park", "dog_park") -> NearbyCategory.PARK
            else -> return null
        }
        val centre = element.optJSONObject("center")
        val latitude = if (element.has("lat")) element.optDouble("lat") else centre?.optDouble("lat")
        val longitude = if (element.has("lon")) element.optDouble("lon") else centre?.optDouble("lon")
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0) return null
        val id = element.optLong("id")
        val osmType = element.optString("type")
        if (id <= 0 || osmType.isBlank()) return null
        val name = tags.optString("name").ifBlank { tags.optString("name:en") }
            .ifBlank {
                context.getString(when (category) {
                    NearbyCategory.VET -> R.string.nearby_unnamed_vet
                    NearbyCategory.GROOMING -> R.string.nearby_unnamed_grooming
                    NearbyCategory.PARK -> R.string.nearby_unnamed_park
                })
            }
        return NearbyOsmPlace("$osmType/$id", category, name, latitude, longitude)
    }
}

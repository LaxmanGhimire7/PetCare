package com.example.petcare.location

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File

/** MapLibre's local raster style; no Google Maps service or billing key is needed. */
object OpenStreetMapTiles {
    // The public OSM tile service is for low-volume development/college demos here. For a
    // production app, switch to a suitable tile provider or self-hosted tile service.
    const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    // Only cluster-count numerals use these free demo glyphs; the basemap is OSM raster tiles.
    private const val GLYPHS_URL = "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf"

    private const val USER_AGENT = "PetCare/2.0 (Android; com.example.petcare)"
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        MapLibre.getInstance(context)
        val client = OkHttpClient.Builder()
            // Respect the tile server's HTTP cache headers instead of re-fetching viewed tiles.
            .cache(Cache(File(context.cacheDir, "osm-tile-http-cache"), 100L * 1024 * 1024))
            .addInterceptor { chain ->
                val request = chain.request()
                val identified = if (request.url.host == "tile.openstreetmap.org") {
                    request.newBuilder().header("User-Agent", USER_AGENT).build()
                } else request
                chain.proceed(identified)
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
        initialized = true
    }

    fun style(): Style.Builder = Style.Builder().fromJson(
        """{
          "version": 8,
          "name": "OpenStreetMap",
          "glyphs": "$GLYPHS_URL",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["$TILE_URL"],
              "tileSize": 256,
              "maxzoom": 19,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [{"id": "osm-raster", "type": "raster", "source": "osm"}]
        }""".trimIndent(),
    )
}

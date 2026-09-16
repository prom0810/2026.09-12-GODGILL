package com.safewalk.map

import android.app.Activity
import android.os.Bundle
import android.os.LocaleList
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.ListView
import android.widget.ArrayAdapter
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.kakao.vectormap.camera.CameraUpdateFactory
import java.util.concurrent.Executors
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.safewalk.BuildConfig
import com.safewalk.R

class MapActivity : Activity() {
    private var mapView: MapView? = null
    private lateinit var status: TextView
    private var kakaoMap: KakaoMap? = null
    private val searchExecutor = Executors.newSingleThreadExecutor()
    private var searching = false
    private lateinit var queryInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchStatus: TextView
    private lateinit var searchResults: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        val container = findViewById<android.widget.FrameLayout>(R.id.map_container)
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        container.requestApplyInsets()
        status = findViewById(R.id.map_status)
        queryInput = findViewById(R.id.search_query)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            queryInput.imeHintLocales = LocaleList.forLanguageTags("ko-KR,en-US")
        }
        searchButton = findViewById(R.id.search_button)
        searchStatus = findViewById(R.id.search_status)
        searchResults = findViewById(R.id.search_results)
        searchButton.setOnClickListener { searchPlaces() }
        queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces()
                true
            } else false
        }
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            status.setText(R.string.map_key_missing)
            return
        }

        val map = MapView(this)
        container.addView(map, 0, android.widget.FrameLayout.LayoutParams(-1, -1))
        mapView = map
        map.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() = Unit

            override fun onMapError(error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        kakaoMap = null
                        status.setText(R.string.map_load_failed)
                        status.visibility = View.VISIBLE
                    }
                }
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        this@MapActivity.kakaoMap = kakaoMap
                        status.visibility = View.GONE
                    }
                }
            }

            // 서울시청을 기본 위치로 사용합니다.
            override fun getPosition(): LatLng = LatLng.from(37.5665, 126.9780)
            override fun getZoomLevel(): Int = 15
        })
    }

    private fun searchPlaces() {
        if (searching) return
        val query = queryInput.text.toString().trim()
        if (query.isEmpty()) {
            queryInput.error = getString(R.string.search_empty_query)
            return
        }
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            searchStatus.setText(R.string.search_key_missing)
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(queryInput.windowToken, 0)
        searching = true
        searchButton.isEnabled = false
        searchResults.visibility = View.GONE
        searchStatus.setText(R.string.search_loading)
        searchExecutor.execute {
            val result = runCatching { KakaoPlaceSearch(BuildConfig.KAKAO_REST_API_KEY).search(query) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                searching = false
                searchButton.isEnabled = true
                result.fold(onSuccess = { places ->
                    searchStatus.text = if (places.isEmpty()) getString(R.string.search_empty)
                        else getString(R.string.search_count, places.size)
                    searchResults.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, places)
                    searchResults.visibility = if (places.isEmpty()) View.GONE else View.VISIBLE
                    searchResults.setOnItemClickListener { _, _, position, _ ->
                        val map = kakaoMap
                        if (map == null) {
                            searchStatus.setText(R.string.search_map_wait)
                        } else {
                            val place = places[position]
                            map.moveCamera(CameraUpdateFactory.newCenterPosition(
                                LatLng.from(place.latitude, place.longitude), 16))
                            searchResults.visibility = View.GONE
                            searchStatus.text = place.toString()
                        }
                    }
                }, onFailure = { error ->
                    searchStatus.setText(when ((error as? PlaceSearchException)?.statusCode) {
                        401, 403 -> R.string.search_auth_error
                        429 -> R.string.search_limit_error
                        else -> R.string.search_failed
                    })
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.resume()
    }

    override fun onPause() {
        mapView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        searchExecutor.shutdownNow()
        kakaoMap = null
        mapView?.finish()
        mapView = null
        super.onDestroy()
    }
}

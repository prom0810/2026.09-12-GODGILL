package com.safewalk.map.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.safewalk.R
import com.safewalk.main.navigation.MainTab
import com.safewalk.main.navigation.bindBottomNavigation
import com.safewalk.map.viewmodel.MapEffect
import java.util.Locale
import kotlin.math.ceil

class RouteComparisonActivity : ComponentActivity() {
    private lateinit var safeCard: View
    private lateinit var shortestCard: View
    private lateinit var safeRadio: RadioButton
    private lateinit var shortestRadio: RadioButton
    private lateinit var startGuideButton: Button
    private var isSafeRouteSelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_comparison)
        bindBottomNavigation(MainTab.MAP)
        configureSystemBars()
        bindComparison()
    }

    private fun configureSystemBars() {
        val container = findViewById<View>(R.id.comparison_container)
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        container.requestApplyInsets()
    }

    private fun bindComparison() {
        val startName = intent.getStringExtra(EXTRA_START_NAME).orEmpty()
        val endName = intent.getStringExtra(EXTRA_END_NAME).orEmpty()
        val shortestDistance = intent.getIntExtra(EXTRA_SHORTEST_DISTANCE, 0)
        val shortestSafety = intent.getIntExtra(EXTRA_SHORTEST_SAFETY, 0)
        val safeDistance = intent.getIntExtra(EXTRA_SAFE_DISTANCE, 0)
        val safeSafety = intent.getIntExtra(EXTRA_SAFE_SAFETY, 0)

        findViewById<TextView>(R.id.route_comparison_endpoints).text = getString(
            R.string.route_comparison_endpoints,
            startName,
            endName,
        )
        findViewById<TextView>(R.id.safe_route_metrics).text = routeMetrics(
            safeDistance,
            safeSafety,
        )
        findViewById<TextView>(R.id.shortest_route_metrics).text = routeMetrics(
            shortestDistance,
            shortestSafety,
        )

        safeCard = findViewById(R.id.safe_route_card)
        shortestCard = findViewById(R.id.shortest_route_card)
        safeRadio = findViewById(R.id.safe_route_radio)
        shortestRadio = findViewById(R.id.shortest_route_radio)
        startGuideButton = findViewById(R.id.start_route_guide_button)

        findViewById<View>(R.id.route_comparison_back).setOnClickListener { finish() }
        safeCard.setOnClickListener { selectRoute(isSafeRoute = true) }
        shortestCard.setOnClickListener { selectRoute(isSafeRoute = false) }
        startGuideButton.setOnClickListener {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_IS_SAFE_ROUTE, isSafeRouteSelected),
            )
            finish()
        }
        selectRoute(isSafeRoute = true)
    }

    private fun selectRoute(isSafeRoute: Boolean) {
        isSafeRouteSelected = isSafeRoute
        safeCard.isSelected = isSafeRoute
        shortestCard.isSelected = !isSafeRoute
        safeRadio.isChecked = isSafeRoute
        shortestRadio.isChecked = !isSafeRoute
        startGuideButton.setText(
            if (isSafeRoute) R.string.start_safe_route_guide else R.string.start_shortest_route_guide,
        )
    }

    private fun routeMetrics(distanceMeters: Int, safetyScore: Int): String = getString(
        R.string.route_comparison_metrics,
        ceil(distanceMeters / WALKING_METERS_PER_MINUTE).toInt().coerceAtLeast(1),
        formatDistance(distanceMeters),
        safetyScore,
    )

    private fun formatDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
        getString(R.string.distance_meters, distanceMeters)
    } else {
        getString(
            R.string.distance_kilometers,
            String.format(Locale.getDefault(), "%.1f", distanceMeters / 1_000.0),
        )
    }

    companion object {
        const val EXTRA_IS_SAFE_ROUTE = "route_is_safe"
        private const val EXTRA_START_NAME = "route_start_name"
        private const val EXTRA_END_NAME = "route_end_name"
        private const val EXTRA_SHORTEST_DISTANCE = "route_shortest_distance"
        private const val EXTRA_SHORTEST_SAFETY = "route_shortest_safety"
        private const val EXTRA_SAFE_DISTANCE = "route_safe_distance"
        private const val EXTRA_SAFE_SAFETY = "route_safe_safety"
        private const val WALKING_METERS_PER_MINUTE = 67.0

        fun createIntent(context: Context, effect: MapEffect.OpenRouteComparison) =
            Intent(context, RouteComparisonActivity::class.java)
                .putExtra(EXTRA_START_NAME, effect.startName)
                .putExtra(EXTRA_END_NAME, effect.endName)
                .putExtra(EXTRA_SHORTEST_DISTANCE, effect.shortestDistanceMeters)
                .putExtra(EXTRA_SHORTEST_SAFETY, effect.shortestSafetyScore)
                .putExtra(EXTRA_SAFE_DISTANCE, effect.safeDistanceMeters)
                .putExtra(EXTRA_SAFE_SAFETY, effect.safeSafetyScore)
    }
}

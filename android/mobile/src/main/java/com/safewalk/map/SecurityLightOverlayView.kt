package com.safewalk.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

data class SecurityLightScreenMarker(val point: Point, val site: SecurityLightSite)

class SecurityLightOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val baseClusterSize = 56f * density
    private val baseMarkerRadius = 6f * density
    private val baseTextSize = 7f * density
    private val touchRadius = 13f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 179, 0) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = baseTextSize
        isFakeBoldText = true
    }
    private var clusters: List<SecurityLightCluster> = emptyList()
    private var pressedCluster: SecurityLightCluster? = null
    var onMarkerClick: ((SecurityLightSite, Int) -> Unit)? = null

    fun setMarkers(markers: List<SecurityLightScreenMarker>, zoomLevel: Int) {
        val scale = markerScaleForZoom(zoomLevel)
        val clusterSize = baseClusterSize * scale.coerceAtLeast(0.35f)
        val markerRadius = baseMarkerRadius * scale.coerceAtLeast(0.45f)
        textPaint.textSize = baseTextSize * scale.coerceAtLeast(0.55f)
        clusters = markers.groupBy {
            Pair((it.point.x / clusterSize).toInt(), (it.point.y / clusterSize).toInt())
        }.values.map { group ->
            SecurityLightCluster(
                x = group.map { it.point.x }.average().toFloat(),
                y = group.map { it.point.y }.average().toFloat(),
                site = group.first().site,
                lightCount = group.sumOf { it.site.lightCount },
                radius = markerRadius,
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        clusters.forEach { cluster ->
            canvas.drawCircle(cluster.x, cluster.y, cluster.radius, fillPaint)
            canvas.drawCircle(cluster.x, cluster.y, cluster.radius, strokePaint)
            canvas.drawText(
                cluster.lightCount.toString(),
                cluster.x,
                cluster.y - (textPaint.ascent() + textPaint.descent()) / 2,
                textPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            pressedCluster = nearestCluster(event.x, event.y)
            pressedCluster != null
        }
        MotionEvent.ACTION_UP -> {
            val cluster = pressedCluster
            pressedCluster = null
            if (cluster != null && nearestCluster(event.x, event.y) == cluster) {
                performClick()
                onMarkerClick?.invoke(cluster.site, cluster.lightCount)
                true
            } else false
        }
        MotionEvent.ACTION_CANCEL -> {
            pressedCluster = null
            false
        }
        else -> pressedCluster != null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestCluster(x: Float, y: Float): SecurityLightCluster? {
        val cluster = clusters.minByOrNull { hypot(x - it.x, y - it.y) } ?: return null
        return cluster.takeIf { hypot(x - it.x, y - it.y) <= touchRadius }
    }

    private fun markerScaleForZoom(@Suppress("UNUSED_PARAMETER") zoomLevel: Int): Float = 1f

    private data class SecurityLightCluster(
        val x: Float,
        val y: Float,
        val site: SecurityLightSite,
        val lightCount: Int,
        val radius: Float,
    )

}

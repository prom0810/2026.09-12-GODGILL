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

data class CctvScreenMarker(val point: Point, val site: CctvSite)

class CctvOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 145, 234) }
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val baseClusterSize = 48f * density
    private val baseMarkerRadius = 6f * density
    private val baseTextSize = 7f * density
    private val touchRadius = 10f * density
    private var markerRadius = baseMarkerRadius
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = baseTextSize
        isFakeBoldText = true
    }
    private var clusters: List<CctvCluster> = emptyList()
    private var pressedMarker: CctvCluster? = null
    var onMarkerClick: ((CctvSite) -> Unit)? = null

    fun setMarkers(value: List<CctvScreenMarker>, zoomLevel: Int) {
        val scale = markerScaleForZoom(zoomLevel)
        val clusterSize = baseClusterSize * scale.coerceAtLeast(0.35f)
        markerRadius = baseMarkerRadius * scale.coerceAtLeast(0.45f)
        textPaint.textSize = baseTextSize * scale.coerceAtLeast(0.55f)
        clusters = value.groupBy {
            Pair((it.point.x / clusterSize).toInt(), (it.point.y / clusterSize).toInt())
        }.values.map { group ->
            CctvCluster(
                x = group.map { it.point.x }.average().toFloat(),
                y = group.map { it.point.y }.average().toFloat(),
                site = group.first().site.copy(cameraCount = group.sumOf { it.site.cameraCount }),
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        clusters.forEach { cluster ->
            canvas.drawCircle(cluster.x, cluster.y, markerRadius, fillPaint)
            if (cluster.site.cameraCount > 1) {
                canvas.drawText(
                    cluster.site.cameraCount.toString(),
                    cluster.x,
                    cluster.y - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint,
                )
            } else {
                canvas.drawCircle(cluster.x, cluster.y, markerRadius * 0.38f, lensPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedMarker = nearestMarker(event.x, event.y)
                pressedMarker != null
            }
            MotionEvent.ACTION_UP -> {
                val marker = pressedMarker
                pressedMarker = null
                if (marker != null && nearestMarker(event.x, event.y) == marker) {
                    performClick()
                    onMarkerClick?.invoke(marker.site)
                    true
                } else false
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedMarker = null
                false
            }
            else -> pressedMarker != null
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestMarker(x: Float, y: Float): CctvCluster? {
        val marker = clusters.minByOrNull { hypot(x - it.x, y - it.y) } ?: return null
        return marker.takeIf { hypot(x - it.x, y - it.y) <= touchRadius }
    }

    private fun markerScaleForZoom(@Suppress("UNUSED_PARAMETER") zoomLevel: Int): Float = 1f

    private data class CctvCluster(val x: Float, val y: Float, val site: CctvSite)
}

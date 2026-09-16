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
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 145, 234) }
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val markerRadius = 7f * resources.displayMetrics.density
    private val touchRadius = 10f * resources.displayMetrics.density
    private var markers: List<CctvScreenMarker> = emptyList()
    private var pressedMarker: CctvScreenMarker? = null
    var onMarkerClick: ((CctvSite) -> Unit)? = null

    fun setMarkers(value: List<CctvScreenMarker>) {
        markers = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        markers.forEach { marker ->
            canvas.drawCircle(marker.point.x.toFloat(), marker.point.y.toFloat(), markerRadius, fillPaint)
            canvas.drawCircle(marker.point.x.toFloat(), marker.point.y.toFloat(), markerRadius * 0.38f, lensPaint)
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

    private fun nearestMarker(x: Float, y: Float): CctvScreenMarker? {
        val marker = markers.minByOrNull { hypot(x - it.point.x, y - it.point.y) } ?: return null
        return marker.takeIf { hypot(x - it.point.x, y - it.point.y) <= touchRadius }
    }
}

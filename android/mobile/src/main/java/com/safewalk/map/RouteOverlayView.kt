package com.safewalk.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.AttributeSet
import android.view.View

/**
 * 경로선(최단/안전)과 출발지·도착지 마커를 화면 좌표에 그리는 오버레이.
 * CctvOverlayView/SecurityLightOverlayView와 동일하게, LatLng -> 화면좌표 변환은
 * MapActivity가 map.toScreenPoint()로 해서 넘겨주고, 이 View는 그리기만 담당한다.
 */
class RouteOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val startFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 118, 210) }
    private val endFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(198, 40, 40) }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val markerRadius = 9f * density

    private var routePoints: List<Point> = emptyList()
    private var startPoint: Point? = null
    private var endPoint: Point? = null

    /** 최단경로=빨강, 안전경로=초록 등 호출부에서 색을 정해 넘긴다. */
    fun setRoute(points: List<Point>, color: Int) {
        routePoints = points
        linePaint.color = color
        invalidate()
    }

    fun setStartEnd(start: Point?, end: Point?) {
        startPoint = start
        endPoint = end
        invalidate()
    }

    fun clear() {
        routePoints = emptyList()
        startPoint = null
        endPoint = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (routePoints.size >= 2) {
            for (i in 0 until routePoints.size - 1) {
                val a = routePoints[i]
                val b = routePoints[i + 1]
                canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), linePaint)
            }
        }
        startPoint?.let {
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), markerRadius, startFillPaint)
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), markerRadius, markerStrokePaint)
        }
        endPoint?.let {
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), markerRadius, endFillPaint)
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), markerRadius, markerStrokePaint)
        }
    }
}

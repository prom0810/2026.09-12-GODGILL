package com.foresto.gatgil.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * 지정한 위경도 사각형(west/south/east/north)에 비트맵 한 장을 고정해서 그리는 오버레이.
 * 지도를 확대/축소/이동해도 항상 같은 지리적 위치에 맞춰 다시 그려진다.
 * (OSMDroid엔 Google Maps의 GroundOverlay 같은 기본 클래스가 없어 직접 구현)
 */
class WmsGroundOverlay(
    private val west: Double,
    private val south: Double,
    private val east: Double,
    private val north: Double
) : Overlay() {

    var bitmap: Bitmap? = null

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val bmp = bitmap ?: return

        val projection = mapView.projection
        val topLeft = projection.toPixels(GeoPoint(north, west), null)
        val bottomRight = projection.toPixels(GeoPoint(south, east), null)

        val rect = Rect(
            minOf(topLeft.x, bottomRight.x),
            minOf(topLeft.y, bottomRight.y),
            maxOf(topLeft.x, bottomRight.x),
            maxOf(topLeft.y, bottomRight.y)
        )

        canvas.drawBitmap(bmp, null, rect, null)
    }
}

package com.safewalk.main.navigation

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import com.safewalk.R
import com.safewalk.map.view.MapActivity
import com.safewalk.mypage.view.MyActivity
import com.safewalk.notification.view.NotificationActivity
import com.safewalk.report.view.ReportActivity

enum class MainTab { MAP, REPORT, NOTIFICATIONS, MY }

@Suppress("DEPRECATION")
fun Activity.bindBottomNavigation(selectedTab: MainTab) {
    val tabs = listOf(
        MainTab.MAP to R.id.nav_map,
        MainTab.REPORT to R.id.nav_report,
        MainTab.NOTIFICATIONS to R.id.nav_notifications,
        MainTab.MY to R.id.nav_my,
    )
    tabs.forEach { (tab, viewId) ->
        val item = findViewById<View>(viewId)
        item.setSelectedRecursively(tab == selectedTab)
        item.setOnClickListener {
            if (tab == selectedTab) return@setOnClickListener
            val target = when (tab) {
                MainTab.MAP -> MapActivity::class.java
                MainTab.REPORT -> ReportActivity::class.java
                MainTab.NOTIFICATIONS -> NotificationActivity::class.java
                MainTab.MY -> MyActivity::class.java
            }
            startActivity(
                Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            overridePendingTransition(0, 0)
        }
    }
}

private fun View.setSelectedRecursively(selected: Boolean) {
    isSelected = selected
    if (this is ViewGroup) {
        repeat(childCount) { getChildAt(it).setSelectedRecursively(selected) }
    }
}

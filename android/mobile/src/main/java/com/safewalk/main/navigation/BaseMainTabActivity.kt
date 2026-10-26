package com.safewalk.main.navigation


import android.os.Bundle
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.annotation.LayoutRes
import com.safewalk.R

abstract class BaseMainTabActivity : ComponentActivity() {
    @get:LayoutRes
    protected abstract val layoutResource: Int
    protected abstract val selectedTab: MainTab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResource)
        val container = findViewById<android.view.View>(R.id.main_container)
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
        bindBottomNavigation(selectedTab)
    }
}

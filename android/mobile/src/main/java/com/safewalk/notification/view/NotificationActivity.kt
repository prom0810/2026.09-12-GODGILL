package com.safewalk.notification.view

import com.safewalk.R
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab

class NotificationActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_notifications
    override val selectedTab: MainTab = MainTab.NOTIFICATIONS
}

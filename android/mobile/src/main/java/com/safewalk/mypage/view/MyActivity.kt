package com.safewalk.mypage.view

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.safewalk.R
import com.safewalk.login.LoginActivity
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab

class MyActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_my
    override val selectedTab: MainTab = MainTab.MY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.my_login_button).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}

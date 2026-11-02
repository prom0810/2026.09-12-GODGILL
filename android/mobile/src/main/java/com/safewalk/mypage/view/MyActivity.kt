package com.safewalk.mypage.view

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.guardian.GuardianActivity
import com.safewalk.login.LoginActivity
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab
import com.safewalk.notification.FcmTokenManager
import kotlinx.coroutines.launch

class MyActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_my
    override val selectedTab: MainTab = MainTab.MY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.my_login_button).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<Button>(R.id.my_guardian_button).setOnClickListener {
            startActivity(Intent(this, GuardianActivity::class.java))
        }
        findViewById<Button>(R.id.my_logout_button).setOnClickListener {
            lifecycleScope.launch {
                FcmTokenManager.unregisterCurrent(this@MyActivity)
                TokenStore(this@MyActivity).clear()
                renderSession()
                startActivity(Intent(this@MyActivity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
        renderSession()
    }

    override fun onResume() {
        super.onResume()
        renderSession()
    }

    private fun renderSession() {
        val store = TokenStore(this)
        val loggedIn = store.isLoggedIn()
        findViewById<Button>(R.id.my_login_button).visibility = if (loggedIn) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.my_guardian_button).visibility = if (loggedIn) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.my_logout_button).visibility = if (loggedIn) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.my_user_title).text = if (loggedIn) store.email() else "비회원 사용자"
        findViewById<TextView>(R.id.my_user_subtitle).text = if (loggedIn) {
            "${if (store.userType() == "MINOR") "미성년 사용자" else "성인 사용자"} · 보호자 연결과 알림을 관리할 수 있어요."
        } else {
            "로그인하면 보호자 연결과 알림 설정을\n저장할 수 있어요."
        }
    }
}

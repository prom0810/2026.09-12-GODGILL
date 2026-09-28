package com.foresto.gatgil

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foresto.gatgil.databinding.ActivityLoginBinding

/**
 * 로그인 화면. 실제 회원 데이터/서버 통신은 하지 않는 "구현 시연용" 화면이다.
 * - 아이디/비밀번호를 입력하고 "로그인"을 누르면, 값이 비어있지 않은지만 확인하고 바로 지도 화면으로 이동한다.
 * - "게스트로 계속하기"를 누르면 입력 없이 바로 이동한다.
 * 실제 회원가입/인증 API 연동은 이번 MVP 범위에서 제외했다 (지도교수 권고에 따른 스코프 조정과 동일한 방침).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            val userId = binding.userIdInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 실제 인증 API 호출은 없음 - 입력값 확인만 하고 바로 다음 화면으로 이동
            goToMap()
        }

        binding.guestButton.setOnClickListener {
            goToMap()
        }
    }

    private fun goToMap() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

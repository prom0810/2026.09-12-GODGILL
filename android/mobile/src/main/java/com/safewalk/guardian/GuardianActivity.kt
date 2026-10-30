package com.safewalk.guardian

import android.content.Intent
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardianActivity : ComponentActivity() {
    private lateinit var repository: GuardianRepository
    private lateinit var status: TextView
    private lateinit var guardianList: LinearLayout
    private lateinit var invitationList: LinearLayout
    private lateinit var wardList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TokenStore(this).isLoggedIn()) {
            openLogin()
            return
        }
        setContentView(R.layout.activity_guardians)
        repository = GuardianRepository(this)
        status = findViewById(R.id.guardian_status)
        guardianList = findViewById(R.id.guardian_list)
        invitationList = findViewById(R.id.invitation_list)
        wardList = findViewById(R.id.ward_list)
        findViewById<View>(R.id.guardian_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.guardian_add).setOnClickListener { showGuardianEditor() }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) loadData()
    }

    private fun loadData() {
        status.text = "불러오는 중…"
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    Triple(repository.guardians(), repository.invitations(), repository.wards())
                }
                renderGuardians(data.first)
                renderInvitations(data.second)
                renderWards(data.third)
                status.text = ""
            } catch (error: Exception) {
                handleError(error)
            }
        }
    }

    private fun renderGuardians(items: List<Guardian>) {
        guardianList.removeAllViews()
        if (items.isEmpty()) return guardianList.addView(emptyMessage("등록된 보호자가 없습니다."))
        items.forEach { guardian ->
            val labels = buildList {
                add(guardian.relationshipLabel)
                if (guardian.isPrimary) add("대표 보호자")
                add(statusLabel(guardian.status))
                if (guardian.canViewLocation) add("위치 열람 허용")
            }
            guardianList.addView(card(
                title = guardian.name,
                description = "${guardian.phone}\n${labels.joinToString(" · ")}",
                actions = buildList {
                    add("수정" to { showGuardianEditor(guardian) })
                    when (guardian.status) {
                        "UNREGISTERED", "REVOKED" -> add("초대" to {
                            runAction("연결 초대를 보냈습니다.") { it.invite(guardian.id) }
                        })
                        "INVITED" -> add("초대 취소" to {
                            runAction("초대를 취소했습니다.") { it.revoke(guardian.id) }
                        })
                        "CONNECTED" -> add("연결 해제" to {
                            confirm("${guardian.name}님과의 연결을 해제할까요?") {
                                runAction("연결을 해제했습니다.") { it.revoke(guardian.id) }
                            }
                        })
                    }
                    add("삭제" to {
                        confirm("${guardian.name}님을 보호자 목록에서 삭제할까요?") {
                            runAction("보호자를 삭제했습니다.") { it.delete(guardian.id) }
                        }
                    })
                },
            ))
        }
    }

    private fun renderInvitations(items: List<GuardianInvitation>) {
        invitationList.removeAllViews()
        if (items.isEmpty()) return invitationList.addView(emptyMessage("받은 요청이 없습니다."))
        items.forEach { invitation ->
            invitationList.addView(card(
                title = "${invitation.wardName}님의 요청",
                description = "관계: ${invitation.relationshipLabel}\n수락해야 보호자 연결이 완료됩니다.",
                actions = listOf(
                    "수락" to { runAction("보호자 연결을 수락했습니다.") { it.accept(invitation.guardianId) } },
                    "거절" to { runAction("요청을 거절했습니다.") { it.reject(invitation.guardianId) } },
                ),
            ))
        }
    }

    private fun renderWards(items: List<Ward>) {
        wardList.removeAllViews()
        if (items.isEmpty()) return wardList.addView(emptyMessage("보호 중인 사용자가 없습니다."))
        items.forEach { ward ->
            val detail = buildList {
                add(ward.wardPhone)
                add(ward.relationshipLabel)
                if (ward.isPrimary) add("대표 보호자")
                add(if (ward.canViewLocation) "위치 열람 허용" else "위치 열람 미허용")
            }.joinToString(" · ")
            wardList.addView(card(
                title = ward.wardName,
                description = detail,
                actions = listOf("연결 끊기" to {
                    confirm("${ward.wardName}님과의 보호자 연결을 끊을까요?") {
                        runAction("연결을 끊었습니다.") { it.leave(ward.guardianId) }
                    }
                }),
            ))
        }
    }

    private fun showGuardianEditor(existing: Guardian? = null) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val name = editText("이름", existing?.name.orEmpty())
        val phone = editText("전화번호", existing?.phone.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            isEnabled = existing == null
        }
        val relationship = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@GuardianActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("부모", "가족", "지인"),
            )
            setSelection(listOf("PARENT", "FAMILY", "ACQUAINTANCE").indexOf(existing?.relationship).coerceAtLeast(0))
        }
        val primary = CheckBox(this).apply {
            text = "대표 보호자로 지정"
            isChecked = existing?.isPrimary ?: false
            isEnabled = existing?.isPrimary != true
        }
        val location = CheckBox(this).apply {
            text = "위치 열람 허용"
            isChecked = existing?.canViewLocation ?: false
        }
        form.addView(name)
        form.addView(phone)
        form.addView(relationship)
        form.addView(primary)
        form.addView(location)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "보호자 또는 지인 추가" else "보호자 정보 수정")
            .setView(form)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (name.text.isBlank() || phone.text.isBlank()) {
                    Toast.makeText(this, "이름과 전화번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val codes = listOf("PARENT", "FAMILY", "ACQUAINTANCE")
                val draft = GuardianDraft(
                    name = name.text.toString().trim(),
                    phone = phone.text.toString().trim(),
                    relationship = codes[relationship.selectedItemPosition],
                    isPrimary = existing?.isPrimary == true || primary.isChecked,
                    canViewLocation = location.isChecked,
                )
                dialog.dismiss()
                runAction(if (existing == null) "보호자를 추가했습니다." else "보호자 정보를 수정했습니다.") {
                    if (existing == null) it.add(draft) else it.update(existing.id, draft)
                }
            }
        }
        dialog.show()
    }

    private fun runAction(successMessage: String, action: (GuardianRepository) -> Unit) {
        status.text = "처리 중…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { action(repository) }
                Toast.makeText(this@GuardianActivity, successMessage, Toast.LENGTH_SHORT).show()
                loadData()
            } catch (error: Exception) {
                handleError(error)
            }
        }
    }

    private fun handleError(error: Exception) {
        if (error is ApiClient.ApiException && error.statusCode == 401) {
            openLogin()
            return
        }
        status.text = error.message ?: "요청을 처리하지 못했습니다."
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton("취소", null)
            .setPositiveButton("확인") { _, _ -> action() }
            .show()
    }

    private fun card(title: String, description: String, actions: List<Pair<String, () -> Unit>>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            setBackgroundResource(R.drawable.map_panel_background)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            addView(TextView(this@GuardianActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#171A19"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@GuardianActivity).apply {
                text = description
                textSize = 12f
                setTextColor(Color.parseColor("#737A77"))
                setPadding(0, dp(7), 0, dp(7))
            })
            addView(LinearLayout(this@GuardianActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                actions.forEach { (label, action) ->
                    addView(Button(this@GuardianActivity).apply {
                        text = label
                        textSize = 11f
                        isAllCaps = false
                        setOnClickListener { action() }
                    }, LinearLayout.LayoutParams(0, dp(42), 1f))
                }
            })
        }

    private fun emptyMessage(message: String) = TextView(this).apply {
        text = message
        textSize = 12f
        setTextColor(Color.parseColor("#737A77"))
        setPadding(0, dp(12), 0, 0)
    }

    private fun editText(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        setSingleLine()
    }

    private fun statusLabel(status: String) = when (status) {
        "INVITED" -> "수락 대기"
        "CONNECTED" -> "연결됨"
        "REVOKED" -> "연결 해제됨"
        else -> "미연결"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

package com.safewalk.report.view

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import com.safewalk.R
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab

class ReportActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_report
    override val selectedTab: MainTab = MainTab.REPORT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.submit_report_button).setOnClickListener {
            val category = findViewById<RadioGroup>(R.id.report_category).checkedRadioButtonId
            val detail = findViewById<EditText>(R.id.report_detail).text.toString().trim()
            if (category == -1 || detail.isEmpty()) {
                Toast.makeText(this, "제보 유형과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "제보가 임시 저장되었습니다.", Toast.LENGTH_SHORT).show()
                findViewById<EditText>(R.id.report_detail).text.clear()
                findViewById<RadioGroup>(R.id.report_category).clearCheck()
            }
        }
    }
}

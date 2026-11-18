package com.safewalk.wear.guide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 안내 경보 알림의 "확인" 버튼·밀어서 지우기 → 경보음·진동을 끈다 */
class GuideAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GuideAlertPlayer.ACTION_DISMISS) GuideAlertPlayer.stop(context)
    }
}

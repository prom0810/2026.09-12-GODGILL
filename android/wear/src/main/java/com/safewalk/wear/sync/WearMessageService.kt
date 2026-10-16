package com.safewalk.wear.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.safewalk.wear.sos.SosManager

/** 폰 -> 워치 메시지 수신. 카운트다운 원격 취소(폰에서 "괜찮아요" 또는 서버 2차 판정이 오탐). */
class WearMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_CANCEL -> SosManager.cancel(applicationContext)
        }
    }
}

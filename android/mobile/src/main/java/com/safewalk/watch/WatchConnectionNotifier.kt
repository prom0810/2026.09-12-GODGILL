package com.safewalk.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safewalk.login.LoginActivity
import com.safewalk.mypage.view.MyActivity

/**
 * 워치 연결 알림 (앱이 백그라운드여도 보이도록 알림으로 띄운다. 앱이 열려 있으면 프론트가
 * [WatchConnectionManager.state]·[WatchConnectionManager.events]를 보고 팝업을 띄울 수도 있다).
 *
 *  - 등록 유도: 미등록 워치에서 데이터가 왔을 때. 누르면 마이페이지(로그인 전이면 로그인)로 이동
 *  - 연결 해제: 연결 중이던 워치의 블루투스 연결이 끊겼을 때
 */
object WatchConnectionNotifier {
    private const val CHANNEL = "safewalk_watch_connection"
    private const val ID_REGISTER = 2101
    private const val ID_DISCONNECTED = 2102

    /** 마이페이지에서 이 값이 true면 워치 등록 화면(프론트)을 바로 띄우면 된다 */
    const val EXTRA_REGISTER_WATCH = "com.safewalk.extra.REGISTER_WATCH"

    fun showRegisterPrompt(context: Context, loggedIn: Boolean) {
        val target = if (loggedIn) {
            Intent(context, MyActivity::class.java).putExtra(EXTRA_REGISTER_WATCH, true)
        } else {
            Intent(context, LoginActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val text = if (loggedIn) {
            "등록되지 않은 워치예요. 워치를 등록해야 워치 기능을 쓸 수 있어요."
        } else {
            "로그인한 뒤 워치를 등록해야 워치 기능을 쓸 수 있어요."
        }
        notify(context, ID_REGISTER, "⌚ 워치를 등록해 주세요", text, target)
    }

    fun cancelRegisterPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REGISTER)
    }

    fun showDisconnected(context: Context) {
        notify(
            context, ID_DISCONNECTED, "⌚ 워치 연결이 해제되었어요",
            "워치와 블루투스 연결이 끊겨 앱 연결을 해제했어요. 다시 연결되면 자동으로 연결돼요.", null,
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String, target: Intent?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "워치 연결", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (target != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context, id, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }
}

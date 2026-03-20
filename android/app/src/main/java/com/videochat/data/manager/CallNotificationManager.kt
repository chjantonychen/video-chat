package com.videochat.data.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.videochat.R
import com.videochat.ui.MainActivity

object CallNotificationManager {
    
    private const val CHANNEL_ID = "video_call_channel"
    private const val NOTIFICATION_ID = 1001
    
    fun showCallNotification(context: Context, remoteUserName: String, isVideoCall: Boolean): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled() == false) {
                android.util.Log.w("CallNotification", "Notifications not enabled, skipping notification")
                return false
            }
        }
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频通话",
                NotificationManager.IMPORTANCE_LOW  // 【关键修复】改为LOW，禁用通知铃声
            ).apply {
                description = "视频通话通知"
                setShowBadge(true)
                setSound(null, null)  // 【关键修复】禁用通知铃声
                enableVibration(false)  // 禁用振动
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知的Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "return_to_call")
            putExtra("remote_user_name", remoteUserName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建通知
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (isVideoCall) "视频通话中" else "语音通话中")
            .setContentText("与 $remoteUserName 通话中，点击返回")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)  // 持续通知，不能被清除
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        return true
    }
    
    fun cancelCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
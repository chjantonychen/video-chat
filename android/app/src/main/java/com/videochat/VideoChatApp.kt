package com.videochat

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.videochat.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoChatApp : Application() {

    companion object {
        private const val TAG = "VideoChatApp"
        // 应用在后台多少毫秒后清除token
        // 30秒是一个合理的值：用户切换应用查看消息后返回，token仍然有效
        // 如果用户关闭了应用，30秒后token将被清除
        private const val BACKGROUND_TIMEOUT_MS = 30_000L // 30秒
    }

    lateinit var preferencesManager: PreferencesManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appLifecycleObserver: AppLifecycleObserver? = null
    private var backgroundCleanupJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "onCreate: 初始化应用")

        // 初始化PreferencesManager
        preferencesManager = PreferencesManager(this)

        // 设置生命周期观察者来检测应用退出
        appLifecycleObserver = AppLifecycleObserver()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver!!)

        // 注册内存压力回调
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                handleTrimMemory(level)
            }

            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                // 不需要处理
            }

            override fun onLowMemory() {
                Log.w(TAG, "onLowMemory: 系统内存不足")
                // 系统内存严重不足，清除token
                clearTokenOnExit("low_memory")
            }
        })
    }

    private fun handleTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.d(TAG, "onTrimMemory: 运行中内存压力 level=$level")
                // 应用仍在运行，但系统需要内存
                // 不清除token，因为用户仍在使用应用
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "onTrimMemory: UI隐藏，应用进入后台")
                // 应用在后台，用户切换到其他应用
                // 不立即清除token，等待BackgroundTimeout
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "onTrimMemory: 系统即将终止进程 level=$level")
                // 系统即将终止进程，立即清除token
                clearTokenOnExit("trim_memory_$level")
            }
        }
    }

    private fun clearTokenOnExit(reason: String) {
        applicationScope.launch {
            try {
                Log.d(TAG, "clearTokenOnExit: 清除token, reason=$reason")
                preferencesManager.clear()
                Log.d(TAG, "clearTokenOnExit: token清除完成")
            } catch (e: Exception) {
                Log.e(TAG, "clearTokenOnExit: 清除token失败", e)
            }
        }
    }

    // 应用生命周期观察者 - 检测应用进入后台
    private inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            Log.d(TAG, "AppLifecycleObserver.onStop: 应用进入后台，将在${BACKGROUND_TIMEOUT_MS}ms后清除token")
            // 应用进入后台，启动延迟清除任务
            // 如果用户在超时时间内返回应用，任务将被取消
            backgroundCleanupJob = applicationScope.launch {
                delay(BACKGROUND_TIMEOUT_MS)
                Log.d(TAG, "AppLifecycleObserver: 后台超时，清除token")
                clearTokenOnExit("background_timeout")
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            Log.d(TAG, "AppLifecycleObserver.onStart: 应用回到前台，取消后台清除任务")
            // 应用回到前台，取消后台清除任务
            backgroundCleanupJob?.cancel()
            backgroundCleanupJob = null
        }
    }
}

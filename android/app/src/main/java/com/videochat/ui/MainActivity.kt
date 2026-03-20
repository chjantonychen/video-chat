package com.videochat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.videochat.data.local.PreferencesManager
import com.videochat.data.repository.AuthRepository
import com.videochat.data.repository.FriendRepository
import com.videochat.data.repository.MessageRepository
import com.videochat.ui.navigation.AppNavigation
import com.videochat.ui.theme.VideoChatTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var authRepository: AuthRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var messageRepository: MessageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查是否从通知栏返回
        val action = intent?.getStringExtra("action")
        
        preferencesManager = PreferencesManager(this)
        authRepository = AuthRepository(preferencesManager)
        friendRepository = FriendRepository()
        messageRepository = MessageRepository()
        
        // 【关键修复】应用启动时从存储恢复token到RetrofitClient
        // 这样API请求才能带上Authorization header
        val savedToken = preferencesManager.getTokenBlocking()
        if (savedToken != null) {
            com.videochat.data.api.RetrofitClient.setToken(savedToken)
            android.util.Log.d("MainActivity", "Restored token from storage to RetrofitClient")
        } else {
            android.util.Log.d("MainActivity", "No token found in storage")
        }

setContent {
            VideoChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 【修复】同步读取登录状态（检查token是否存在）
                    val isLoggedIn = remember { 
                        val hasToken = authRepository.isLoggedInSync()
                        android.util.Log.d("MainActivity", "isLoggedInSync: hasToken=$hasToken")
                        hasToken
                    }

                    val navController = rememberNavController()
                    
                    // 如果是从通知栏返回，导航到通话界面
                    LaunchedEffect(action) {
                        if (action == "return_to_call") {
                            // 先清除intent的action，防止重复触发
                            intent?.removeExtra("action")
                            
                            // 延迟一点确保导航已初始化
                            kotlinx.coroutines.delay(100)
                            // 获取CallViewModel中的通话信息
                            val callViewModel = com.videochat.ui.viewmodel.CallViewModel.getInstance(application)
                            val callState = callViewModel.callState.value
                            
                            // 只有通话正在进行中才导航到CallScreen
                            val isConnected = callState is com.videochat.ui.viewmodel.CallState.Connected
                            val isConnecting = callState is com.videochat.ui.viewmodel.CallState.Connecting
                            val isCalling = callState is com.videochat.ui.viewmodel.CallState.Calling
                            val isRinging = callState is com.videochat.ui.viewmodel.CallState.Ringing
                            
                            android.util.Log.d("MainActivity", "action=return_to_call, callState=$callState, isConnected=$isConnected")
                            
                            if (isConnected || isConnecting || isCalling || isRinging) {
                                val route = "call/0/${callViewModel.isVideoCall}/true/${callViewModel.remoteUserId}"
                                navController.navigate(route)
                            } else {
                                android.util.Log.d("MainActivity", "Call not active, skipping navigation to CallScreen")
                            }
                        }
                    }

                    AppNavigation(
                        navController = navController,
                        isLoggedIn = isLoggedIn,
                        authRepository = authRepository,
                        friendRepository = friendRepository,
                        messageRepository = messageRepository,
                        preferencesManager = preferencesManager
                    )
                }
            }
        }
    }
}

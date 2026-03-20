package com.videochat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.videochat.data.local.PreferencesManager
import com.videochat.data.repository.AuthRepository
import com.videochat.data.repository.FriendRepository
import com.videochat.data.repository.MessageRepository
import com.videochat.ui.screens.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Chat : Screen("chat/{friendId}/{isVideoCall}") {
        fun createRoute(friendId: Long, isVideoCall: Boolean = false) = "chat/$friendId/$isVideoCall"
    }
    object Call : Screen("call/{callId}/{isVideo}/{isCaller}/{remoteUserId}") {
        fun createRoute(callId: Long, isVideo: Boolean, isCaller: Boolean, remoteUserId: Long) = "call/$callId/$isVideo/$isCaller/$remoteUserId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    isLoggedIn: Boolean,
    authRepository: AuthRepository,
    friendRepository: FriendRepository,
    messageRepository: MessageRepository,
    preferencesManager: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                authRepository = authRepository
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                authRepository = authRepository
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToChat = { friendId, isVideoCall ->
                    navController.navigate(Screen.Chat.createRoute(friendId, isVideoCall))
                },
                onNavigateToCall = { callId, isVideo, isCaller, remoteUserId ->
                    navController.navigate(Screen.Call.createRoute(callId, isVideo, isCaller, remoteUserId))
                },
                onLogout = {
                    // 清除token后再导航
                    scope.launch {
                        authRepository.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                friendRepository = friendRepository,
                preferencesManager = preferencesManager
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("friendId") { type = NavType.LongType },
                navArgument("isVideoCall") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getLong("friendId") ?: 0L
            val isVideoCall = backStackEntry.arguments?.getBoolean("isVideoCall") ?: false
            
            // 【关键修复】使用 rememberInBackStack 来跟踪是否已经发起过通话
            // 当从 CallScreen 返回时，isVideoCall 应该被忽略，防止重复发起
            androidx.compose.runtime.remember(isVideoCall) {
                android.util.Log.d("Navigation", "ChatScreen composed with isVideoCall=$isVideoCall")
            }
            
            ChatScreen(
                friendId = friendId,
                isVideoCall = isVideoCall,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { isVideo ->
                    // 【关键修复】发起新通话前重置CallViewModel状态
                    val app = navController.context.applicationContext as android.app.Application
                    val callViewModel = com.videochat.ui.viewmodel.CallViewModel.getInstance(app)
                    callViewModel.resetState()
                    android.util.Log.d("Navigation", "Reset CallViewModel state before starting new call")
                    
                    navController.navigate(Screen.Call.createRoute(0, isVideo, true, friendId))
                },
                messageRepository = messageRepository,
                preferencesManager = preferencesManager
            )
        }
        
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.LongType },
                navArgument("isVideo") { type = NavType.BoolType },
                navArgument("isCaller") { type = NavType.BoolType },
                navArgument("remoteUserId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getLong("callId") ?: 0L
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val isCaller = backStackEntry.arguments?.getBoolean("isCaller") ?: false
            val remoteUserId = backStackEntry.arguments?.getLong("remoteUserId") ?: 0L
            
            android.util.Log.d("Navigation", "========== CallScreen params ==========")
            android.util.Log.d("Navigation", "callId=$callId, isVideo=$isVideo, isCaller=$isCaller, remoteUserId=$remoteUserId")
            
            CallScreen(
                callId = callId,
                isVideo = isVideo,
                isCaller = isCaller,
                remoteUserId = remoteUserId,
                onEndCall = { navController.popBackStack() }
            )
        }
    }
}

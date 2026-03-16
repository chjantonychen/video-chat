package com.videochat.ui.navigation

import androidx.compose.runtime.Composable
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

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Chat : Screen("chat/{friendId}") {
        fun createRoute(friendId: Long) = "chat/$friendId"
    }
    object Call : Screen("call/{callId}/{isVideo}/{isCaller}") {
        fun createRoute(callId: Long, isVideo: Boolean, isCaller: Boolean) = "call/$callId/$isVideo/$isCaller"
    }
}

@Composable
fun AppNavigation(navController: NavHostController, isLoggedIn: Boolean, authRepository: AuthRepository, friendRepository: FriendRepository, messageRepository: MessageRepository, preferencesManager: PreferencesManager) {
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
                onNavigateToChat = { friendId ->
                    navController.navigate(Screen.Chat.createRoute(friendId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                friendRepository = friendRepository
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("friendId") { type = NavType.LongType })
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getLong("friendId") ?: 0L
            ChatScreen(
                friendId = friendId,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { isVideo ->
                    navController.navigate(Screen.Call.createRoute(0, isVideo, true))
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
                navArgument("isCaller") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getLong("callId") ?: 0L
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val isCaller = backStackEntry.arguments?.getBoolean("isCaller") ?: true
            CallScreen(
                callId = callId,
                isVideo = isVideo,
                isCaller = isCaller,
                onEndCall = { navController.popBackStack() }
            )
        }
    }
}

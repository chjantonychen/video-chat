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
        
        preferencesManager = PreferencesManager(this)
        authRepository = AuthRepository(preferencesManager)
        friendRepository = FriendRepository()
        messageRepository = MessageRepository()
        
        setContent {
            VideoChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isLoggedIn by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(Unit) {
                        isLoggedIn = authRepository.isLoggedIn()
                    }
                    
                    val navController = rememberNavController()
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

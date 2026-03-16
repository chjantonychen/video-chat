package com.videochat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.videochat.data.local.PreferencesManager
import com.videochat.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    authRepository: AuthRepository
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "注册",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("昵称（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    val result = authRepository.register(username, password, nickname.ifEmpty { null })
                    result.onSuccess {
                        isLoading = false
                        onRegisterSuccess()
                    }.onFailure { e ->
                        isLoading = false
                        errorMessage = e.message ?: "Registration failed"
                    }
                }
            },
            enabled = !isLoading && username.isNotEmpty() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("注册")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateToLogin) {
            Text("已有账号？立即登录")
        }
    }
}

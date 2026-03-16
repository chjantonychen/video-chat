package com.videochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callId: Long,
    isVideo: Boolean,
    isCaller: Boolean,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isCameraOn by remember { mutableStateOf(isVideo) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }
    
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (isVideo) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isEmpty()) {
            hasPermissions = true
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVideo) "视频通话" else "语音通话") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Remote video or avatar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    // Remote video surface placeholder
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("等待连接...")
                        }
                    }
                } else {
                    // Voice call - show avatar
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }
            
            // Call controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Mute button
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "静音",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Speaker button
                IconButton(
                    onClick = { isSpeakerOn = !isSpeakerOn },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "扬声器",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Camera button (video only)
                if (isVideo) {
                    IconButton(
                        onClick = { isCameraOn = !isCameraOn },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "摄像头",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // End call button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "结束通话",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

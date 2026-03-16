package com.videochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videochat.ui.viewmodel.CallState
import com.videochat.ui.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callId: Long,
    isVideo: Boolean,
    isCaller: Boolean,
    remoteUserId: Long,
    onEndCall: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()

    // 初始化通话
    LaunchedEffect(callId) {
        viewModel.initializeCall(callId, remoteUserId, isVideo, isCaller)
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (callState) {
                is CallState.Idle -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is CallState.Calling -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("等待对方接听...")
                    }
                }

                is CallState.Ringing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("来电")
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            FilledIconButton(
                                onClick = { viewModel.acceptCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "接听",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            FilledIconButton(
                                onClick = { viewModel.rejectCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "拒绝",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                is CallState.Connected -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 远程视频或占位符
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo) {
                                // 远程视频渲染占位符
                                // 实际需要通过WebRTC流获取远程视频
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "对方视频",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("通话中...")
                                }
                            }
                        }

                        // 通话控制按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 静音按钮
                            IconButton(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isMuted)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "静音",
                                    modifier = Modifier.size(32.dp),
                                    tint = if (isMuted) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 扬声器按钮
                            IconButton(
                                onClick = { viewModel.toggleSpeaker() },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isSpeakerOn)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "扬声器",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // 摄像头按钮（视频通话）
                            if (isVideo) {
                                IconButton(
                                    onClick = { viewModel.toggleCamera() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (!isCameraOn)
                                            MaterialTheme.colorScheme.errorContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(
                                        if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = "摄像头",
                                        modifier = Modifier.size(32.dp),
                                        tint = if (!isCameraOn) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 切换摄像头按钮
                                IconButton(
                                    onClick = { viewModel.switchCamera() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Cameraswitch,
                                        contentDescription = "切换摄像头",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // 结束通话按钮
                            IconButton(
                                onClick = {
                                    viewModel.endCall()
                                    onEndCall()
                                },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
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

                is CallState.Ended -> {
                    LaunchedEffect(Unit) {
                        onEndCall()
                    }
                }

                is CallState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "通话错误: ${(callState as CallState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onEndCall) {
                            Text("返回")
                        }
                    }
                }
            }
        }
    }
}
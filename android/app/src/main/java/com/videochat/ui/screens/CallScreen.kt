package com.videochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videochat.ui.viewmodel.CallState
import com.videochat.ui.viewmodel.CallViewModel
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

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
    
    LaunchedEffect(callState) {
        android.util.Log.d("CallScreen", "========== callState changed: $callState ==========")
        
        // 记录状态转换
        when (callState) {
            is CallState.Calling -> {
                android.util.Log.d("CallScreen", "State: Calling - should show local preview only")
            }
            is CallState.Connecting -> {
                android.util.Log.d("CallScreen", "State: Connecting - should show small local preview")
            }
            is CallState.Connected -> {
                android.util.Log.d("CallScreen", "State: Connected - should show remote video (top) and local preview (bottom)")
                android.util.Log.d("CallScreen", "Resetting WebRTC views to clear old SurfaceView references")
                viewModel.resetViews()
            }
            is CallState.Ended -> {
                android.util.Log.d("CallScreen", "State: Ended - cleaning up")
            }
            else -> {}
        }
    }

    // SurfaceViewRenderers现在在各个状态中创建，避免重复创建

    LaunchedEffect(callId) {
        viewModel.initializeCall(callId, remoteUserId, isVideo, isCaller)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
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
                title = { Text(if (isVideo) "视频通话" else "语音通话") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.forceEndCall(); onEndCall() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is CallState.Calling -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                // 使用remember创建SurfaceViewRenderer，避免重复创建
                                val callingLocalVideoView = remember {
                                    SurfaceViewRenderer(context).apply {
                                        // 可以在初始化时进行基本配置
                                    }
                                }
                                
                                // 当SurfaceViewRenderer可用时，通知ViewModel
                                LaunchedEffect(callingLocalVideoView) {
                                    viewModel.startLocalVideo(callingLocalVideoView)
                                }
                                
                                AndroidView(
                                    factory = { callingLocalVideoView },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("等待对方接听...")
                        Spacer(modifier = Modifier.height(24.dp))
                        // 取消呼叫按钮
                        FilledIconButton(
                            onClick = { viewModel.forceEndCall(); onEndCall() },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "取消呼叫", modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                }

                is CallState.Ringing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Phone, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("来电")
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            FilledIconButton(
                                onClick = { viewModel.acceptCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "接听", modifier = Modifier.size(32.dp))
                            }
                            FilledIconButton(
                                onClick = { viewModel.rejectCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "拒绝", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                is CallState.Connecting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在连接...")
                        
                        // 视频通话时显示本地预览
                        if (isVideo) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(160.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                // 使用remember创建SurfaceViewRenderer，避免重复创建
                                val connectingLocalVideoView = remember {
                                    SurfaceViewRenderer(context).apply {
                                        // 可以在初始化时进行基本配置
                                    }
                                }
                                
                                // 当SurfaceViewRenderer可用时，通知ViewModel
                                LaunchedEffect(connectingLocalVideoView) {
                                    viewModel.startLocalVideo(connectingLocalVideoView)
                                }
                                
                                AndroidView(
                                    factory = { connectingLocalVideoView },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("预览", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is CallState.Connected -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 远程视频 (大屏)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo) {
                                // 使用remember创建SurfaceViewRenderer，避免重复创建
                                val remoteVideoSurfaceView = remember {
                                    SurfaceViewRenderer(context).apply {
                                        // 可以在初始化时进行基本配置
                                    }
                                }
                                
                                // 当SurfaceViewRenderer可用时，通知ViewModel
                                LaunchedEffect(remoteVideoSurfaceView) {
                                    viewModel.setRemoteVideoView(remoteVideoSurfaceView)
                                }
                                
                                AndroidView(
                                    factory = { remoteVideoSurfaceView },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Person, contentDescription = null,
                                        modifier = Modifier.size(120.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("通话中...")
                                }
                            }
                        }

                        // 本地视频预览 (小屏)
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(8.dp)
                            ) {
                                // 使用remember创建SurfaceViewRenderer，避免重复创建
                                val localVideoView = remember {
                                    SurfaceViewRenderer(context).apply {
                                        // 可以在初始化时进行基本配置
                                    }
                                }
                                
                                // 当SurfaceViewRenderer可用时，通知ViewModel
                                LaunchedEffect(localVideoView) {
                                    viewModel.startLocalVideo(localVideoView)
                                }
                                
                                AndroidView(
                                    factory = { localVideoView },
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(160.dp)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
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

                            IconButton(
                                onClick = { viewModel.toggleSpeaker() },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isSpeakerOn) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "扬声器",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            if (isVideo) {
                                IconButton(
                                    onClick = { viewModel.toggleCamera() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (!isCameraOn) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
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

                                IconButton(
                                    onClick = { viewModel.switchCamera() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Cameraswitch, contentDescription = "切换", modifier = Modifier.size(32.dp))
                                }
                            }

IconButton(
    onClick = { viewModel.forceEndCall(); onEndCall() },
    modifier = Modifier.size(56.dp),
    colors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer
    )
) {
                                Icon(
                                    Icons.Default.CallEnd, contentDescription = "结束",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

is CallState.Ended -> {
    android.util.Log.d("CallScreen", "CallState.Ended, calling onEndCall")
    // 显示通话结束提示
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CallEnd, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("通话已结束", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onEndCall) { Text("返回") }
    }
    // 同时使用LaunchedEffect确保自动返回
    LaunchedEffect(Unit) {
        delay(1500) // 显示1.5秒后自动返回
        onEndCall()
    }
}

is CallState.CallError -> {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error, contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("通话错误: ${(callState as CallState.CallError).message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onEndCall) { Text("返回") }
                    }
                }
            }
        }
    }
}
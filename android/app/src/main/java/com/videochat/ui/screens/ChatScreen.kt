package com.videochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.videochat.data.api.RetrofitClient
import com.videochat.data.local.PreferencesManager
import com.videochat.data.manager.CallManager  // 【新增】导入CallManager
import com.videochat.data.model.MESSAGE_TYPE_IMAGE
import com.videochat.data.model.MESSAGE_TYPE_TEXT
import com.videochat.data.model.MESSAGE_TYPE_VOICE
import com.videochat.data.repository.MessageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(
    val id: Long,
    val content: String,
    val fromUserId: Long,
    val fromUsername: String,
    val isFromMe: Boolean,
    val type: Int = MESSAGE_TYPE_TEXT
)

// 【关键修复】使用对象来保存通话状态，这样可以在Activity重建和Compose重组时保持状态
object ChatScreenCallState {
    val hasAutoStartedForFriend = mutableMapOf<Long, Boolean>()
    
    fun markCallStarted(friendId: Long) {
        hasAutoStartedForFriend[friendId] = true
        android.util.Log.d("ChatScreen", "Marked call started for friendId=$friendId")
    }
    
    fun hasStartedCall(friendId: Long): Boolean {
        return hasAutoStartedForFriend[friendId] ?: false
    }
    
    fun reset(friendId: Long) {
        hasAutoStartedForFriend.remove(friendId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    friendId: Long,
    isVideoCall: Boolean = false,
    onNavigateBack: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    messageRepository: MessageRepository,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<Long?>(null) }
    var currentUsername by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var playingVoiceId by remember { mutableStateOf<Long?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // 【关键修复】使用静态状态对象来保存通话标志，防止Compose重组时丢失
    var hasAutoStartedCall by remember { 
        mutableStateOf(ChatScreenCallState.hasStartedCall(friendId)) 
    }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "image_${System.currentTimeMillis()}.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
messageRepository.uploadFile(tempFile).onSuccess { url ->
                Log.d("ChatScreen", "Image uploaded: $url")
                messageRepository.sendImageMessage(friendId, url).onSuccess {
                    messageRepository.getMessageList(friendId).onSuccess { msgList ->
                        messages = msgList.map { msg ->
                            ChatMessage(
                                id = msg.id,
                                content = msg.content,
                                fromUserId = msg.fromUserId,
                                fromUsername = if (msg.fromUserId == currentUserId) currentUsername else "对方",
                                isFromMe = msg.fromUserId == currentUserId,
                                type = msg.type
                            )
                        }
                    }
                }.onFailure { e ->
                    Toast.makeText(context, "发送图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                Log.e("ChatScreen", "Upload failed", e)
                Toast.makeText(context, "上传图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
                } catch (e: Exception) {
                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Voice permission
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "录音功能需要更多代码实现", Toast.LENGTH_SHORT).show()
        }
    }

    // Load user info and messages
    LaunchedEffect(friendId) {
        Log.d("ChatScreen", "Loading user info and messages for friendId=$friendId")
        
        // First load user info from local storage
        var userId = preferencesManager.getUserIdSync()
        var username = preferencesManager.getUsernameSync()
        Log.d("ChatScreen", "Local storage - userId=$userId, username=$username")

        // If not found in local storage, try to fetch from API
        if (userId == null || username == null) {
            try {
                Log.d("ChatScreen", "Fetching user info from API...")
                val userResponse = RetrofitClient.apiService.getMe()
                Log.d("ChatScreen", "API response: ${userResponse.code()}")
                if (userResponse.isSuccessful) {
                    userResponse.body()?.let { user ->
                        userId = user.id
                        username = user.username
                        Log.d("ChatScreen", "Got user from API: id=${user.id}, username=${user.username}")
                        // Save to local storage for future use
                        preferencesManager.saveUserInfo(user.id, user.username)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to fetch user from API", e)
            }
        }

        currentUserId = userId
        currentUsername = username ?: ""

        // Then load messages with currentUserId available
        if (userId != null) {
            Log.d("ChatScreen", "Loading messages for userId=$userId")
            
            // Load messages function
            suspend fun loadMessages() {
                val currentId = preferencesManager.getUserIdSync()
                val currentName = preferencesManager.getUsernameSync()
                if (currentId != null) {
                    messageRepository.getMessageList(friendId).onSuccess { msgList ->
                        messages = msgList.map { msg ->
                            ChatMessage(
                                id = msg.id,
                                content = msg.content,
                                fromUserId = msg.fromUserId,
                                fromUsername = if (msg.fromUserId == currentId) (currentName ?: "我") else "对方",
                                isFromMe = msg.fromUserId == currentId,
                                type = msg.type
                            )
                        }
                    }
                }
            }
            
            // Load immediately
            loadMessages()
            
            // Start polling for new messages every 3 seconds
            while (true) {
                delay(3000)
                loadMessages()
            }
        } else {
            Log.w("ChatScreen", "userId is null, not loading messages")
        }
    }
    
    // 【关键修复】使用独立的 LaunchedEffect(Unit) 确保只自动发起一次通话
    LaunchedEffect(Unit) {
        if (isVideoCall && !hasAutoStartedCall) {
            // 等待 userId 加载
            var userId = preferencesManager.getUserIdSync()
            if (userId == null) {
                try {
                    val userResponse = RetrofitClient.apiService.getMe()
                    if (userResponse.isSuccessful) {
                        userId = userResponse.body()?.id
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Failed to fetch user", e)
                }
            }
            
            if (userId != null) {
                hasAutoStartedCall = true
                // 【关键修复】使用静态对象保存状态，防止Compose重组时丢失
                ChatScreenCallState.markCallStarted(friendId)
                android.util.Log.d("ChatScreen", "========== Auto-starting video call (once) ==========")
                android.util.Log.d("ChatScreen", "friendId=$friendId, userId=$userId")
                // 延迟一点时间再导航，让当前Composition完成
                delay(100)
                onStartCall(true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 【关键修复】检查是否正在通话中，禁用通话按钮
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val callManager = remember {
                        com.videochat.data.manager.CallManager.getInstance(
                            context.applicationContext as android.app.Application
                        )
                    }
                    // 直接读取 isInCall 属性，Compose 会自动观察变化
                    val isInCall = callManager.isInCall
                    
                    IconButton(
                        onClick = { Log.d("ChatScreen", "Voice call button clicked"); onStartCall(false) },
                        enabled = !isInCall  // 通话中禁用
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "语音通话")
                    }

                    IconButton(
                        onClick = { Log.d("ChatScreen", "Video call button clicked"); onStartCall(true) },
                        enabled = !isInCall  // 通话中禁用
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "视频通话")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
                items(messages) { message ->
                    ChatMessageItem(
                        message = message,
                        onImageClick = { },
                        playingVoiceId = playingVoiceId,
                        onVoicePlay = { msg ->
                            // Play voice message
                            try {
                                if (playingVoiceId == msg.id) {
                                    // Stop playing
                                    mediaPlayer?.let {
                                        if (it.isPlaying) it.stop()
                                        it.release()
                                    }
                                    mediaPlayer = null
                                    playingVoiceId = null
                                } else {
                                    // Stop any current playback
                                    mediaPlayer?.let {
                                        if (it.isPlaying) it.stop()
                                        it.release()
                                    }
                                    mediaPlayer = null
                                    
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(msg.content)
                                        prepare()
                                        start()
                                        setOnCompletionListener {
                                            playingVoiceId = null
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                        }
                                    }
                                    playingVoiceId = msg.id
                                }
                            } catch (e: Exception) {
                                Log.e("ChatScreen", "Play voice failed", e)
                                playingVoiceId = null
                            }
                        }
                    )
                }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "图片", tint = MaterialTheme.colorScheme.primary)
                }

IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    // Start/stop recording
                    if (isRecording) {
                        // Stop recording
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            mediaRecorder = null
                            isRecording = false
                            
// Upload voice file
                            voiceFile?.let { file ->
                                scope.launch {
                                    messageRepository.uploadFile(file).onSuccess { url ->
                                        messageRepository.sendVoiceMessage(friendId, url).onSuccess {
                                            // Reload messages
                                            val currentId = preferencesManager.getUserIdSync()
                                            val currentName = preferencesManager.getUsernameSync()
                                            if (currentId != null) {
                                                messageRepository.getMessageList(friendId).onSuccess { msgList ->
                                                    messages = msgList.map { msg ->
                                                        ChatMessage(
                                                            id = msg.id,
                                                            content = msg.content,
                                                            fromUserId = msg.fromUserId,
                                                            fromUsername = if (msg.fromUserId == currentId) (currentName ?: "我") else "对方",
                                                            isFromMe = msg.fromUserId == currentId,
                                                            type = msg.type
                                                        )
                                                    }
                                                }
                                            }
                                        }.onFailure { e ->
                                            Toast.makeText(context, "发送语音失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { e ->
                                        Toast.makeText(context, "上传语音失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Start recording
                        try {
                            voiceFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }.apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setOutputFile(voiceFile?.absolutePath)
                                prepare()
                                start()
                            }
                            isRecording = true
                            Toast.makeText(context, "开始录音...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "开始录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止录音" else "语音",
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("发送消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )

IconButton(
            onClick = {
                if (messageText.isNotEmpty()) {
                    scope.launch {
                        Log.d("ChatScreen", "Sending message to friendId=$friendId, content=$messageText")
                        messageRepository.sendMessage(
                            toUserId = friendId,
                            type = MESSAGE_TYPE_TEXT,
                            content = messageText
                        ).onSuccess {
                            Log.d("ChatScreen", "Message sent successfully")
                            messageText = ""
                            messageRepository.getMessageList(friendId).onSuccess { msgList ->
                                Log.d("ChatScreen", "Reloaded ${msgList.size} messages")
                                messages = msgList.map { msg ->
                                    val isFromMe = msg.fromUserId == currentUserId
                                    Log.d("ChatScreen", "Message id=${msg.id}, fromUserId=${msg.fromUserId}, currentUserId=$currentUserId, isFromMe=$isFromMe")
                                    ChatMessage(
                                        id = msg.id,
                                        content = msg.content,
                                        fromUserId = msg.fromUserId,
                                        fromUsername = if (isFromMe) currentUsername else "对方",
                                        isFromMe = isFromMe,
                                        type = msg.type
                                            )
                                        }
                                    }
                                }.onFailure { e ->
                                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = messageText.isNotEmpty()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onImageClick: (String) -> Unit,
    playingVoiceId: Long?,
    onVoicePlay: (ChatMessage) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Text(
                message.fromUsername.ifEmpty { "用户${message.fromUserId}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            when (message.type) {
                MESSAGE_TYPE_IMAGE -> {
                    Log.d("ChatScreen", "Loading image: ${message.content}")
                    AsyncImage(
                        model = message.content,
                        contentDescription = "图片",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.content) },
                        contentScale = ContentScale.Crop
                    )
                }
                MESSAGE_TYPE_VOICE -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onVoicePlay(message) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (playingVoiceId == message.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "语音消息",
                                color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(12.dp),
                            color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
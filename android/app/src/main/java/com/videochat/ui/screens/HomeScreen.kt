package com.videochat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import android.content.Context
import com.videochat.data.local.PreferencesManager
import com.videochat.data.manager.CallManager
import com.videochat.data.model.FriendRequest
import com.videochat.data.model.User
import com.videochat.data.repository.FriendRepository
import kotlinx.coroutines.launch

data class FriendItem(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatar: String?,
    val isOnline: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: (Long, Boolean) -> Unit,
    onNavigateToCall: (Long, Boolean, Boolean, Long) -> Unit,
    onLogout: () -> Unit,
    friendRepository: FriendRepository,
    preferencesManager: com.videochat.data.local.PreferencesManager
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    var friendRequests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 初始化CallManager来接收来电通知
    LaunchedEffect(Unit) {
        android.util.Log.d("HomeScreen", "========== HomeScreen LaunchedEffect START ==========")
        val token = preferencesManager.getTokenSync()
        val userId = preferencesManager.getUserIdSync()
        android.util.Log.d("HomeScreen", "token=${token != null}, userId=$userId")
        if (token != null && userId != null) {
            val app = context.applicationContext as android.app.Application
            android.util.Log.d("HomeScreen", "Initializing CallManager for userId=$userId")
            val callManager = CallManager.getInstance(app)
            callManager.callListener = object : CallManager.CallListener {
                override fun onIncomingCall(callId: Long, fromUserId: Long, callType: Int, callerName: String) {
                    android.util.Log.d("HomeScreen", "========== onIncomingCall ==========")
                    android.util.Log.d("HomeScreen", "callId=$callId, fromUserId=$fromUserId, callType=$callType")
                    // 收到来电，跳转到通话界面
                    val isVideo = callType == 2
                    onNavigateToCall(callId, isVideo, false, fromUserId)
                }

                override fun onCallResponse(callId: Long, accept: Boolean, toUserId: Long?) {
                    android.util.Log.d("HomeScreen", "========== onCallResponse ==========")
                    android.util.Log.d("HomeScreen", "callId=$callId, accept=$accept, toUserId=$toUserId")
                    // 【修复】主叫收到对方接受响应时，不在这里处理
                    // 通话逻辑由CallScreen的CallViewModel处理
                    // HomeScreen不应该响应call_response，否则会导致挂断后再次发起通话
                    android.util.Log.d("HomeScreen", "Call response ignored in HomeScreen - CallScreen handles it")
                }

                override fun onCallEnded(callId: Long) {
                    android.util.Log.d("HomeScreen", "========== onCallEnded ==========")
                    android.util.Log.d("HomeScreen", "callId=$callId")
                    // 对方结束了通话，不需要特殊处理，会在CallScreen中处理
                }
            }
            callManager.connect(token, userId)
            android.util.Log.d("HomeScreen", "CallManager.connect called")
        } else {
            android.util.Log.w("HomeScreen", "Token or userId is null, cannot initialize CallManager")
        }
        android.util.Log.d("HomeScreen", "========== HomeScreen LaunchedEffect END ==========")
    }

    LaunchedEffect(selectedTab) {
        errorMessage = null
        if (selectedTab == 0) {
            isLoading = true
            try {
                friendRepository.getFriendList()
                    .onSuccess { list ->
                        Log.d("HomeScreen", "Friend list loaded: ${list.size} items")
                        friends = list
                    }
                    .onFailure { e ->
                        Log.e("HomeScreen", "Failed to load friend list", e)
                        errorMessage = "加载好友列表失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Exception loading friend list", e)
                errorMessage = "加载好友列表异常: ${e.message}"
            }
            isLoading = false
        } else if (selectedTab == 1) {
            isLoading = true
            try {
                friendRepository.getFriendRequests()
                    .onSuccess { list ->
                        Log.d("HomeScreen", "Friend requests loaded: ${list.size} items")
                        list.forEach { req ->
                            Log.d("HomeScreen", "Request: id=${req.id}, fromUserId=${req.fromUserId}, fromUsername=${req.fromUsername}, fromNickname=${req.fromNickname}")
                        }
                        friendRequests = list
                    }
                    .onFailure { e ->
                        Log.e("HomeScreen", "Failed to load friend requests", e)
                        errorMessage = "加载好友请求失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Exception loading friend requests", e)
                errorMessage = "加载好友请求异常: ${e.message}"
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频聊天") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "退出登录")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddFriendDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "添加好友")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("好友") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("请求") }
                )
            }

            // 显示错误消息
            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when (selectedTab) {
                0 -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (friends.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无好友，点击+添加好友")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(friends) { friend ->
                                FriendListItem(
                                    friend = FriendItem(
                                        id = friend.id,
                                        username = friend.username,
                                        nickname = friend.nickname ?: friend.username,
                                        avatar = friend.avatar
                                    ),
                                    onClick = { onNavigateToChat(friend.id, false) },
                                    onVideoCall = { onNavigateToCall(0, true, true, friend.id) },
                                    onVoiceCall = { onNavigateToCall(0, false, true, friend.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (friendRequests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无好友请求")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(friendRequests) { request ->
                                FriendRequestItem(
                                    request = request,
                                    onAccept = {
                                        scope.launch {
                                            friendRepository.acceptFriendRequest(request.id)
                                                .onSuccess {
                                                    friendRequests = friendRequests.filter { it.id != request.id }
                                                }
                                                .onFailure { e ->
                                                    Log.e("HomeScreen", "Accept failed", e)
                                                    errorMessage = "接受请求失败: ${e.message}"
                                                }
                                        }
                                    },
                                    onReject = {
                                        scope.launch {
                                            friendRepository.rejectFriendRequest(request.id)
                                                .onSuccess {
                                                    friendRequests = friendRequests.filter { it.id != request.id }
                                                }
                                                .onFailure { e ->
                                                    Log.e("HomeScreen", "Reject failed", e)
                                                    errorMessage = "拒绝请求失败: ${e.message}"
                                                }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            friendRepository = friendRepository,
            onDismiss = { showAddFriendDialog = false }
        )
    }
}

@Composable
fun FriendListItem(
    friend: FriendItem,
    onClick: () -> Unit,
    onVideoCall: () -> Unit = onClick,
    onVoiceCall: () -> Unit = onClick
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                CircleAvatar()
                if (friend.isOnline) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Online",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(friend.nickname, style = MaterialTheme.typography.titleMedium)
                Text(friend.username, style = MaterialTheme.typography.bodySmall)
            }

            IconButton(onClick = { Log.d("HomeScreen", "Voice call button clicked for ${friend.username}"); onVoiceCall() }) {
                Icon(Icons.Default.Call, contentDescription = "Voice Call")
            }

            IconButton(onClick = { Log.d("HomeScreen", "Video call button clicked for ${friend.username}"); onVideoCall() }) {
                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
            }
        }
    }
}

@Composable
fun FriendRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleAvatar()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 添加null安全处理
                val displayName = if (request.fromNickname.isNullOrBlank()) request.fromUsername else request.fromNickname
                Text(displayName ?: "未知用户", style = MaterialTheme.typography.titleMedium)
                Text(request.fromUsername ?: "", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CircleAvatar() {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, contentDescription = null)
        }
    }
}

@Composable
fun AddFriendDialog(
    friendRepository: FriendRepository,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<User?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requestSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好友") },
        text = {
            Column {
                if (!requestSent) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            searchResult = null
                            errorMessage = null
                        },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSearching = true
                                errorMessage = null
                                friendRepository.searchUser(username).onSuccess { user ->
                                    searchResult = user
                                    if (user == null) {
                                        errorMessage = "用户不存在"
                                    }
                                }.onFailure {
                                    errorMessage = "搜索失败"
                                }
                                isSearching = false
                            }
                        },
                        enabled = username.isNotEmpty() && !isSearching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("搜索")
                        }
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    searchResult?.let { user ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircleAvatar()
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.nickname ?: user.username, style = MaterialTheme.typography.titleSmall)
                                    Text(user.username, style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            friendRepository.sendFriendRequest(user.id).onSuccess {
                                                requestSent = true
                                            }.onFailure {
                                                errorMessage = "发送请求失败"
                                            }
                                        }
                                    }
                                ) {
                                    Text("添加")
                                }
                            }
                        }
                    }
                } else {
                    Text("好友请求已发送！", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            if (!requestSent) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
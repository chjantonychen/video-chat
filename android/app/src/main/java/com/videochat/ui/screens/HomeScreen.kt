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
import android.app.Application  // 【新增】导入Application
import android.content.Context
import com.videochat.data.local.PreferencesManager
import com.videochat.data.manager.CallManager
import com.videochat.data.model.FriendRequest
import com.videochat.data.model.User
import com.videochat.data.repository.FriendRepository
import com.videochat.ui.viewmodel.CallState
import com.videochat.ui.viewmodel.CallViewModel
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
    val scope = rememberCoroutineScope()
    
    // 获取CallViewModel单例，监听通话状态
    val callViewModel = remember { 
        CallViewModel.getInstance(context.applicationContext as android.app.Application) 
    }
    val callState by callViewModel.callState.collectAsState()
    val hasActiveCall = callState is CallState.Calling || callState is CallState.Ringing || 
                        callState is CallState.Connecting || callState is CallState.Connected

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
                    // 【修复】不在HomeScreen播放铃声，由CallScreen处理
                    // 收到来电，跳转到通话界面
                    val isVideo = callType == 2
                    onNavigateToCall(callId, isVideo, false, fromUserId)
                }
                
                override fun onCallResponse(callId: Long, accept: Boolean, toUserId: Long?) {
                    android.util.Log.d("HomeScreen", "========== onCallResponse ==========")
                    android.util.Log.d("HomeScreen", "callId=$callId, accept=$accept, toUserId=$toUserId")
                    // 主叫收到对方接受响应，跳转到通话界面
                    if (accept && toUserId != null) {
                        onNavigateToCall(0, true, true, toUserId)
                    }
                }
                
                override fun onCallEnded(callId: Long) {
                    android.util.Log.d("HomeScreen", "========== onCallEnded ==========")
                    android.util.Log.d("HomeScreen", "callId=$callId")
                    // 通话结束，由CallScreen处理
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
        if (selectedTab == 0) {
            isLoading = true
            friendRepository.getFriendList().onSuccess { friends = it }
            isLoading = false
        } else if (selectedTab == 1) {
            isLoading = true
            friendRepository.getFriendRequests().onSuccess { friendRequests = it }
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
        // 没有通话时显示"添加好友"按钮
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
    onVideoCall = { onNavigateToChat(friend.id, true) },
    onVoiceCall = { onNavigateToChat(friend.id, false) }
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
                                            friendRequests = friendRequests.filter { it.id != request.id }
                                        }
                                    },
                                    onReject = {
                                        scope.launch {
                                            friendRepository.rejectFriendRequest(request.id)
                                            friendRequests = friendRequests.filter { it.id != request.id }
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
    // 【关键修复】检查是否正在通话中，禁用通话按钮
    val context = LocalContext.current
    val callManager = remember {
        CallManager.getInstance(context.applicationContext as Application)
    }
    // 直接读取 isInCall 属性，Compose 会自动观察变化
    val isInCall = callManager.isInCall
    
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
            
            IconButton(
                onClick = { Log.d("HomeScreen", "Voice call button clicked for ${friend.username}"); onVoiceCall() },
                enabled = !isInCall  // 通话中禁用
            ) {
                Icon(Icons.Default.Call, contentDescription = "Voice Call")
            }

            IconButton(
                onClick = { Log.d("HomeScreen", "Video call button clicked for ${friend.username}"); onVideoCall() },
                enabled = !isInCall  // 通话中禁用
            ) {
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
                Text(request.fromNickname ?: request.fromUsername, style = MaterialTheme.typography.titleMedium)
                Text(request.fromUsername, style = MaterialTheme.typography.bodySmall)
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

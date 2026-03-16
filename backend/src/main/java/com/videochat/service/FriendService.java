package com.videochat.service;

import com.videochat.dto.FriendRequestDTO;
import com.videochat.dto.UserDTO;
import java.util.List;

public interface FriendService {
    void sendFriendRequest(Long fromUserId, Long toUserId);
    List<FriendRequestDTO> getFriendRequests(Long userId);
    void acceptFriendRequest(Long requestId, Long userId);
    void rejectFriendRequest(Long requestId, Long userId);
    List<UserDTO> getFriendList(Long userId);
    void deleteFriend(Long userId, Long friendId);
}

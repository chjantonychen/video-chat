package com.videochat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.videochat.dto.FriendRequestDTO;
import com.videochat.dto.UserDTO;
import com.videochat.mapper.FriendMapper;
import com.videochat.mapper.FriendRequestMapper;
import com.videochat.mapper.UserMapper;
import com.videochat.model.Friend;
import com.videochat.model.FriendRequest;
import com.videochat.model.User;
import com.videochat.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {
    
    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final UserMapper userMapper;
    
    @Override
    public void sendFriendRequest(Long fromUserId, Long toUserId) {
        // Check if already friends
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, fromUserId).eq(Friend::getFriendId, toUserId);
        if (friendMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("Already friends");
        }
        
        // Check if request already exists
        LambdaQueryWrapper<FriendRequest> reqWrapper = new LambdaQueryWrapper<>();
        reqWrapper.eq(FriendRequest::getFromUserId, fromUserId)
                  .eq(FriendRequest::getToUserId, toUserId)
                  .eq(FriendRequest::getStatus, 0);
        if (friendRequestMapper.selectCount(reqWrapper) > 0) {
            throw new RuntimeException("Request already sent");
        }
        
        FriendRequest request = new FriendRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setStatus(0);
        request.setCreatedAt(LocalDateTime.now());
        friendRequestMapper.insertWithId(request);
    }
    
    @Override
    public List<FriendRequestDTO> getFriendRequests(Long userId) {
        LambdaQueryWrapper<FriendRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendRequest::getToUserId, userId)
               .eq(FriendRequest::getStatus, 0)
               .orderByDesc(FriendRequest::getCreatedAt);
        
        List<FriendRequest> requests = friendRequestMapper.selectList(wrapper);
        List<Long> userIds = requests.stream().map(FriendRequest::getFromUserId).collect(Collectors.toList());
        
        if (userIds.isEmpty()) return new ArrayList<>();
        
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        
        return requests.stream()
            .map(req -> {
                User user = userMap.get(req.getFromUserId());
                if (user == null) return null;
                return new FriendRequestDTO(
                    req.getId(),
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getAvatar()
                );
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void acceptFriendRequest(Long requestId, Long userId) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null || !request.getToUserId().equals(userId)) {
            throw new RuntimeException("Request not found");
        }
        
        // Update request status
        request.setStatus(1);
        friendRequestMapper.updateById(request);
        
        // Add friends (both directions)
        addFriendRelationship(request.getFromUserId(), userId);
        addFriendRelationship(userId, request.getFromUserId());
    }
    
    @Override
    public void rejectFriendRequest(Long requestId, Long userId) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null || !request.getToUserId().equals(userId)) {
            throw new RuntimeException("Request not found");
        }
        request.setStatus(2);
        friendRequestMapper.updateById(request);
    }
    
    @Override
    public List<UserDTO> getFriendList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId).eq(Friend::getStatus, 1);
        List<Friend> friends = friendMapper.selectList(wrapper);
        
        List<Long> friendIds = friends.stream().map(Friend::getFriendId).collect(Collectors.toList());
        if (friendIds.isEmpty()) return new ArrayList<>();
        
        List<User> users = userMapper.selectBatchIds(friendIds);
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        LambdaQueryWrapper<Friend> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId);
        friendMapper.delete(wrapper1);
        
        LambdaQueryWrapper<Friend> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(Friend::getUserId, friendId).eq(Friend::getFriendId, userId);
        friendMapper.delete(wrapper2);
    }
    
    private void addFriendRelationship(Long userId, Long friendId) {
        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        friend.setStatus(1);
        friend.setCreatedAt(LocalDateTime.now());
        friendMapper.insertWithId(friend);
    }
    
    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setSignature(user.getSignature());
        return dto;
    }
}

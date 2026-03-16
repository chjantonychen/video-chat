package com.videochat.service;

import com.videochat.model.Message;
import java.util.List;

public interface MessageService {
    Message sendMessage(Long fromUserId, Long toUserId, Integer type, String content);
    List<Message> getMessageList(Long userId, Long friendId, Integer page, Integer size);
    void markAsRead(Long userId, Long friendId);
}

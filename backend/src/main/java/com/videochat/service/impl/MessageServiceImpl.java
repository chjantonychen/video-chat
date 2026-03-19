package com.videochat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.videochat.mapper.MessageMapper;
import com.videochat.model.Message;
import com.videochat.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    
    private final MessageMapper messageMapper;
    
    @Override
    public Message sendMessage(Long fromUserId, Long toUserId, Integer type, String content) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setType(type);
        message.setContent(content);
        message.setIsRead(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insertWithId(message);
        return message;
    }
    
    @Override
    public List<Message> getMessageList(Long userId, Long friendId, Integer page, Integer size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
            .eq(Message::getFromUserId, userId).eq(Message::getToUserId, friendId)
            .or()
            .eq(Message::getFromUserId, friendId).eq(Message::getToUserId, userId)
        );
        wrapper.orderByAsc(Message::getCreatedAt);
        
        Page<Message> messagePage = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(messagePage, wrapper);
        return result.getRecords();
    }
    
    @Override
    public void markAsRead(Long userId, Long friendId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getFromUserId, friendId)
               .eq(Message::getToUserId, userId)
               .eq(Message::getIsRead, 0);
        
        List<Message> messages = messageMapper.selectList(wrapper);
        messages.forEach(msg -> {
            msg.setIsRead(1);
            messageMapper.updateById(msg);
        });
    }
}

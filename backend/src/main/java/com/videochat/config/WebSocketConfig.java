package com.videochat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }
    
@Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP over SockJS (for web)
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS();
        
        // 原生WebSocket (for Android)
        registry.addEndpoint("/ws-native")
            .setAllowedOrigins("*");
    }
}

package com.surveillance.facedetection.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Enables WebSocket with STOMP protocol.
 * Browser connects to /ws, subscribes to /topic/alerts.
 * Server pushes AlertNotificationDTO to /topic/alerts when match found.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Browser subscribes to topics prefixed with /topic
        registry.enableSimpleBroker("/topic");
        // Messages FROM browser go to @MessageMapping methods prefixed with /app
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket handshake endpoint — browser connects here
        // SockJS fallback for browsers that don't support native WebSocket
        registry.addEndpoint("/ws").withSockJS();
    }
}

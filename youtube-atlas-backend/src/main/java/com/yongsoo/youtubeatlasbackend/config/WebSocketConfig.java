package com.yongsoo.youtubeatlasbackend.config;

import java.security.Principal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AtlasProperties atlasProperties;
    private final ObjectProvider<AuthService> authServiceProvider;

    public WebSocketConfig(AtlasProperties atlasProperties, ObjectProvider<AuthService> authServiceProvider) {
        this.atlasProperties = atlasProperties;
        this.authServiceProvider = authServiceProvider;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getMessageType() != SimpMessageType.CONNECT) {
                    return message;
                }

                String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                if (authorizationHeader == null) {
                    authorizationHeader = accessor.getFirstNativeHeader("authorization");
                }
                if (authorizationHeader == null) {
                    return message;
                }

                AuthenticatedUser user = authServiceProvider.getObject().requireCurrentUser(authorizationHeader);
                accessor.setUser(new UserPrincipal(user.id().toString()));
                return message;
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(atlasProperties.getRealtime().getAllowedOrigins().toArray(String[]::new));
    }

    private record UserPrincipal(String name) implements Principal {

        @Override
        public String getName() {
            return name;
        }
    }
}

package config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WsConfig implements WebSocketMessageBrokerConfigurer {

    private final WsJwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WsPrincipalHandshakeHandler principalHandshakeHandler;

    public WsConfig(WsJwtHandshakeInterceptor jwtHandshakeInterceptor, WsPrincipalHandshakeHandler principalHandshakeHandler) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(principalHandshakeHandler)
                .setAllowedOriginPatterns("*");
    }
}



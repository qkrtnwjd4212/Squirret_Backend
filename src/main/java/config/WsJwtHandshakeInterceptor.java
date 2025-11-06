package config;

import com.squirret.squirretbackend.service.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public WsJwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query == null || !query.contains("token=")) {
            return false;
        }
        String token = null;
        for (String part : query.split("&")) {
            if (part.startsWith("token=")) {
                token = part.substring(6);
                break;
            }
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            String subjectEmail = jwtService.extractUsername(token);
            attributes.put("principalName", subjectEmail);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
    }
}



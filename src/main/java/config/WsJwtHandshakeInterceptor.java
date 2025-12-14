package config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * 게스트 모드용 WebSocket 핸드셰이크 인터셉터
 * 토큰 없이도 연결 허용 (게스트 ID만 사용)
 */
@Component
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 게스트 모드에서는 항상 허용, 게스트 ID 생성
        String guestId = UUID.randomUUID().toString();
        attributes.put("principalName", "guest-" + guestId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
    }
}



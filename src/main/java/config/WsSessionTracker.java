package config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionTracker {

    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    public Set<String> getActiveUsers() {
        return activeUsers;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal p = sha.getUser();
        if (p != null && p.getName() != null) {
            activeUsers.add(p.getName());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal p = sha.getUser();
        if (p != null && p.getName() != null) {
            activeUsers.remove(p.getName());
        }
    }
}



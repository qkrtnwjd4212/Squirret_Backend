package com.squirret.squirretbackend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.entity.WsMessageLog;
import com.squirret.squirretbackend.repository.WsMessageLogRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class SessionWsController {

    private final WsMessageLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public SessionWsController(WsMessageLogRepository logRepository, ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/session.message")
    public void handle(Map<String, Object> req, Principal principal) throws JsonProcessingException {
        String actor = principal != null ? principal.getName() : "anonymous";
        String type = (String) req.getOrDefault("type", "UNKNOWN");
        String payloadJson = objectMapper.writeValueAsString(req);

        WsMessageLog log = new WsMessageLog();
        log.setActorId(actor);
        log.setType(type);
        log.setPayload(payloadJson);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);

        Map<String, Object> response = Map.of(
                "ok", true,
                "type", type,
                "echo", req,
                "ts", System.currentTimeMillis()
        );

        // SimpMessagingTemplate을 사용하여 사용자에게 메시지 전송
        messagingTemplate.convertAndSendToUser(actor, "/queue/session", response);
    }
}



package com.squirret.squirretbackend.repository;

import com.squirret.squirretbackend.entity.WsSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WsSessionRepository extends JpaRepository<WsSession, Long> {
    Optional<WsSession> findBySessionId(String sessionId);
}



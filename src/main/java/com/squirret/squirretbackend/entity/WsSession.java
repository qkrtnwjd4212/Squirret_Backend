package com.squirret.squirretbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ws_session")
@Getter
@Setter
@NoArgsConstructor
public class WsSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_ref", nullable = false)
    private WsIdentity identity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}



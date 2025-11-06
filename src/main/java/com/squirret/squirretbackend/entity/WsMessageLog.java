package com.squirret.squirretbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ws_message_log",
        indexes = {
                @Index(name = "idx_actor_created", columnList = "actor_id, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class WsMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "type", nullable = false)
    private String type;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}



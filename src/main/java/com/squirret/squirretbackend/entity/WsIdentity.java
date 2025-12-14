package com.squirret.squirretbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ws_identity",
        indexes = {
                @Index(name = "uk_user_id", columnList = "user_id", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class WsIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", unique = true)
    private String guestId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}



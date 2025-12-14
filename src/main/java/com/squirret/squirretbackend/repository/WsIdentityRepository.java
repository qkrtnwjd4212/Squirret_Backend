package com.squirret.squirretbackend.repository;

import com.squirret.squirretbackend.entity.WsIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WsIdentityRepository extends JpaRepository<WsIdentity, Long> {
    Optional<WsIdentity> findByGuestId(String guestId);
    Optional<WsIdentity> findByUserId(String userId);
}



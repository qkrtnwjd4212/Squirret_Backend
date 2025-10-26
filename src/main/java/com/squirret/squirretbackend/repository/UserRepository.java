package com.squirret.squirretbackend.repository;

import com.squirret.squirretbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByProviderAndProviderId(User.Provider provider, String providerId);
    
    boolean existsByEmail(String email);
    
    boolean existsByProviderAndProviderId(User.Provider provider, String providerId);
}

package com.readshelf.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Lookup on presentation. Deterministic hash in, matching row out (or empty).
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Reuse-detection nuke: revoke every token sharing a lineage in one statement.
    // (Alternative: load the family and setRevoked(true) on each — your call in the service.)
    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = :familyId")
    void revokeFamily(@Param("familyId") UUID familyId);
}

package com.readshelf.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    // Underscore = explicit relation traversal (profile.user.id), same idiom as
    // ReviewRepository.findByBook_Id. A profile is looked up by its OWNER's id, not its own PK.
    Optional<UserProfile> findByUser_Id(UUID userId);
}
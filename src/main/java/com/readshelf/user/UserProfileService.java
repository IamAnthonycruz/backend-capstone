package com.readshelf.user;

import com.readshelf.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Profile read/write. This is the class the Phase 10 cache annotations hang on:
 * caching lives on the SERVICE (a Spring proxy), never the controller — and never on a
 * method another method in this same class calls directly (self-invocation bypasses the proxy).
 *
 * getByUserId is the cache-aside READ; upsert is the WRITE that must invalidate.
 */
@Service
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final UserProfileMapper profileMapper;

    public UserProfileService(UserProfileRepository profileRepository,
                              UserRepository userRepository,
                              UserProfileMapper profileMapper) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.profileMapper = profileMapper;
    }


    @Cacheable(value= CacheConfig.USER_PROFILES, key = "#userId")
    public UserProfileResponseDTO getByUserId(UUID userId) {
        return profileRepository.findByUser_Id(userId)
                .map(profileMapper::toResponseDTO)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    @CacheEvict(value = CacheConfig.USER_PROFILES, key = "#userId")
    public UserProfileResponseDTO upsert(UUID userId, UserProfileRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " not found"));

        UserProfile profile = profileRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    UserProfile fresh = profileMapper.toEntity(request);
                    fresh.setUser(owner);
                    return fresh;
                });

        // update path: overwrite the scalar fields on the existing (or freshly built) entity
        profile.setDisplayName(request.displayName());
        profile.setBio(request.bio());
        profile.setLocation(request.location());
        profile.setProfilePictureUrl(request.profilePictureUrl());

        return profileMapper.toResponseDTO(profileRepository.save(profile));
    }
}
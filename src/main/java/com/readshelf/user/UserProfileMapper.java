package com.readshelf.user;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

/**
 * Scalar-only mapping. Like the other relationship entities, the mapper never resolves
 * the owning User — the service attaches the resolved User onto the entity.
 */
@Component
public class UserProfileMapper implements EntityMapper<UserProfileRequestDTO, UserProfileResponseDTO, UserProfile> {

    @Override
    public UserProfile toEntity(UserProfileRequestDTO request) {
        UserProfile profile = new UserProfile();
        profile.setDisplayName(request.displayName());
        profile.setBio(request.bio());
        profile.setLocation(request.location());
        profile.setProfilePictureUrl(request.profilePictureUrl());
        return profile;
    }

    @Override
    public UserProfileResponseDTO toResponseDTO(UserProfile p) {
        return new UserProfileResponseDTO(
                p.getId(),
                p.getUser().getId(),
                p.getDisplayName(),
                p.getBio(),
                p.getLocation(),
                p.getProfilePictureUrl(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
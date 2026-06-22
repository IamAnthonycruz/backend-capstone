package com.readshelf.user;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class UserMapper implements EntityMapper<UserRequestDTO, UserResponseDTO, User> {

    @Override
    public User toEntity(UserRequestDTO request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        // ⚠️ Phase 2 stores the password AS-IS (plaintext). This is a known gap.
        // TODO(Phase 5): hash with BCrypt before persisting; never store raw credentials.
        user.setPassword(request.password());
        user.setRole(request.role());
        return user;
    }

    @Override
    public UserResponseDTO toResponseDTO(User user) {
        // password intentionally omitted
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
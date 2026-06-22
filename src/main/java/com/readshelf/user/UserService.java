package com.readshelf.user;

import com.readshelf.utils.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors BookService exactly (Controller -> Service -> Repository; service owns the
 * mapper + repo and deals in DTOs). No new logic vs. Book — User has no outgoing FK
 * references to resolve.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public PagedResponse<UserResponseDTO> findAll(int page, int size, UserSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(userRepository.findAll(pageable).map(userMapper::toResponseDTO));
    }

    public Optional<UserResponseDTO> findById(UUID id) {
        return userRepository.findById(id).map(userMapper::toResponseDTO);
    }

    public UserResponseDTO create(UserRequestDTO request) {
        User saved = userRepository.save(userMapper.toEntity(request));
        return userMapper.toResponseDTO(saved);
    }

    public Optional<UserResponseDTO> update(UUID id, UserRequestDTO request) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        User user = existing.get();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password()); // TODO(Phase 5): hash before storing
        user.setRole(request.role());
        userRepository.save(user);
        return Optional.of(userMapper.toResponseDTO(user));
    }

    public boolean delete(UUID id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }
}
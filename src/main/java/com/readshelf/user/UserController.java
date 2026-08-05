package com.readshelf.user;

import com.readshelf.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;

    public UserController(UserService userService, UserProfileService userProfileService) {
        this.userService = userService;
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponseDTO>> listUsers(
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "USERNAME") UserSortField sortBy
    ) {
        return ResponseEntity.ok(userService.findAll(page, size, sortBy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable UUID id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // TODO(human): a user may only update THEIR OWN profile. Add @PreAuthorize comparing
    // the current user's id against the {id} being updated. Both are available here:
    //   - #id                      -> the path variable (a java.util.UUID)
    //   - authentication.principal -> what JwtAuthFilter set (the userId as a String)
    // Wrinkle: UUID != String, so == won't match as-is. Make the two sides the same type.
    @PreAuthorize("authentication.principal == #id.toString()")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable UUID id,
                                                      @Valid @RequestBody UserRequestDTO request) {
        return userService.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // --- Nested profile resource (Phase 10 caching target) ---
    // Rooted under the parent (/users/{id}/profile), so UserController owns it — same
    // convention as /books/{id}/reviews. The service does the cache-aside read.
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponseDTO> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(userProfileService.getByUserId(id));
    }

    // Upsert: create the profile if absent, update if present. Owner-only (same rule as PUT user).
    @PreAuthorize("authentication.principal == #id.toString()")
    @PutMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponseDTO> updateProfile(@PathVariable UUID id,
                                                                @Valid @RequestBody UserProfileRequestDTO request) {
        return ResponseEntity.ok(userProfileService.upsert(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        return userService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
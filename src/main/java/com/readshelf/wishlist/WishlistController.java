package com.readshelf.wishlist;

import com.readshelf.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<WishlistResponseDTO>> listWishlists(
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "CREATED_AT") WishlistSortField sortBy
    ) {
        return ResponseEntity.ok(wishlistService.findAll(page, size, sortBy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WishlistResponseDTO> getWishlist(@PathVariable UUID id) {
        return wishlistService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WishlistResponseDTO> createWishlist(@Valid @RequestBody WishlistRequestDTO request) {
        WishlistResponseDTO created = wishlistService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // No PUT — nothing to update on a wishlist entry.

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWishlist(@PathVariable UUID id) {
        return wishlistService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
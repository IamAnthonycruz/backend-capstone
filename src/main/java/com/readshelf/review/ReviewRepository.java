package com.readshelf.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findByBook_Id(UUID bookId, Pageable pageable);

    /**
     * Unpaged sibling of the above, for the search indexer: a book document embeds ALL of its
     * review text, so there is no page to ask for. Paging exists to protect an HTTP response
     * from returning too much at once, which isn't the constraint here.
     */
    List<Review> findByBook_Id(UUID bookId);
}
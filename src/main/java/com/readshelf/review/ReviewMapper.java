package com.readshelf.review;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper implements EntityMapper<ReviewRequestDTO, ReviewResponseDTO, Review> {

    // NOTE: toEntity sets only the SCALAR fields. The generic mapper has no access to
    // repositories, so it can't resolve userId/bookId -> entities. That resolution lives
    // in ReviewService, which sets user/book on the returned Review before saving.
    @Override
    public Review toEntity(ReviewRequestDTO request) {
        Review review = new Review();
        review.setRating(request.rating());
        review.setContent(request.content());
        return review;
    }

    @Override
    public ReviewResponseDTO toResponseDTO(Review review) {
        // getUser()/getBook() are lazy proxies, but getId() reads the FK without a query.
        return new ReviewResponseDTO(
                review.getId(),
                review.getUser().getId(),
                review.getBook().getId(),
                review.getRating(),
                review.getContent()
        );
    }
}
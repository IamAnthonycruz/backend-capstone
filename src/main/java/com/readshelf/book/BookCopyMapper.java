package com.readshelf.book;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class BookCopyMapper implements EntityMapper<BookCopyRequestDTO, BookCopyResponseDTO, BookCopy> {

    // Scalars only; the service attaches the resolved book + owner.
    @Override
    public BookCopy toEntity(BookCopyRequestDTO request) {
        BookCopy copy = new BookCopy();
        if (request.isAvailable() != null) {
            copy.setAvailable(request.isAvailable());
        }
        return copy;
    }

    @Override
    public BookCopyResponseDTO toResponseDTO(BookCopy copy) {
        return new BookCopyResponseDTO(
                copy.getId(),
                copy.getBook().getId(),
                copy.getOwner().getId(),
                copy.isAvailable()
        );
    }
}
package com.readshelf.book;

import com.readshelf.utils.EntityMapper;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@NoArgsConstructor
@Component
public class BookMapper implements EntityMapper<BookRequestDTO, BookResponseDTO, Book> {
    @Override
    public Book toEntity(BookRequestDTO bookRequestDTO) {
        Book book = new Book();
        book.setIsbn(bookRequestDTO.isbn());
        book.setGenre(bookRequestDTO.genre());
        book.setTitle(bookRequestDTO.title());
        book.setAuthor(bookRequestDTO.author());
        book.setSummary(bookRequestDTO.summary());
        return book;
    }

    @Override
    public BookResponseDTO toResponseDTO(Book book) {
        return new BookResponseDTO(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getGenre(),
                book.getSummary(),
                book.getUpdatedAt()
        );
    }
}

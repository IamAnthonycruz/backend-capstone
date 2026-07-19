package com.readshelf.book;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {

    // 🟢 Easy query challenge: all books that have at least one AVAILABLE copy.
    //
    // The SQL you designed:
    //   SELECT DISTINCT b.*
    //   FROM   books b
    //   JOIN   book_copies bc ON b.id = bc.book_id
    //   WHERE  bc.is_available = true;
    //
    // Translate it to JPQL using the table above:
    //   - entities (Book / BookCopy), not table names
    //   - field names (isAvailable), not column names
    //   - join on the mapped relationship: JOIN BookCopy bc ON bc.book = b
    //   - SELECT DISTINCT b   (return whole Book entities)
    //
    // TODO(human): fill in the JPQL between the quotes.
    @Query(value = "SELECT DISTINCT b.* " +
            "FROM books b " +
            "JOIN book_copies bc ON b.id = bc.book_id " +
            "WHERE bc.is_available = true", nativeQuery = true)
    List<Book> findBooksWithAvailableCopy();

    String bookWithAverageRatingAndReviewCountQuery = """
            SELECT b.id, b.title, AVG(r.rating), COUNT(r.id)
            FROM books b
            LEFT JOIN reviews r ON r.book_id = b.id
            GROUP BY b.id
            """;
    @Query(value = bookWithAverageRatingAndReviewCountQuery, nativeQuery = true)
    List<Object[]> getAllBooksAverageRatingAndReviewCount();

    // v2 book detail: one book's fields + review aggregates, built straight into the DTO.
    @Query("SELECT new com.readshelf.book.BookDetailV2DTO(" +
            "b.id, b.isbn, b.title, b.author, b.genre, b.description, " +
            "AVG(r.rating), COUNT(r.id)) " +
            "FROM Book b LEFT JOIN b.reviews r " +
            "WHERE b.id = :id " +
            "GROUP BY b.id, b.isbn, b.title, b.author, b.genre, b.description")
    Optional<BookDetailV2DTO> findBookDetailById(@Param("id") UUID id);
}
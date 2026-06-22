package com.readshelf.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookCopyRepository extends JpaRepository<BookCopy, UUID> {
}
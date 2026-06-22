package com.readshelf.loan;

import com.readshelf.book.BookCopy;
import com.readshelf.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

enum LoanStatus {
    REQUESTED,
    APPROVED,
    ACTIVE,
    RETURNED,
    OVERDUE
}

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loans")
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LoanStatus status;
    @CreationTimestamp
    @Column(name = "request_date", nullable = false)
    private Instant requestDate;
    @Column(name = "approval_date")
    private Instant approvalDate;
    @Column(name = "due_date")
    private Instant dueDate;
    @Column(name = "return_date")
    private Instant returnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", referencedColumnName = "id", nullable = false)
    private User lender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", referencedColumnName = "id", nullable = false)
    private User borrower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_copy_id",referencedColumnName = "id", nullable = false)
    private BookCopy bookCopy;

}

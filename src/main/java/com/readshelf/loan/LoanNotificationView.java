package com.readshelf.loan;

/**
 * Projection carrying exactly the fields a loan notification needs — nothing else.
 *
 * Loading the Loan ENTITY for this pulls in lender and borrower, and each User eagerly drags
 * its UserProfile along (@OneToOne defaults to EAGER, and the mappedBy side can't be made lazy
 * without bytecode enhancement). That's three queries for a handful of strings. Projecting
 * straight into this record asks the database for four columns and stops there: one query, no
 * entities, no associations to accidentally traverse after the session closes.
 *
 * Both parties' address AND name are here because the direction flips per event: loan.requested
 * emails the lender about the borrower, loan.approved and loan.overdue email the borrower about
 * the lender. One projection serving both directions beats two nearly-identical queries.
 */
public record LoanNotificationView(String lenderEmail,
                                   String lenderUsername,
                                   String borrowerEmail,
                                   String borrowerUsername) {
}

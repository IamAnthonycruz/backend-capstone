package com.readshelf.loan;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class LoanMapper implements EntityMapper<LoanRequestDTO, LoanResponseDTO, Loan> {

    // Scalars only (dueDate). The service attaches lender/borrower/bookCopy and sets status.
    @Override
    public Loan toEntity(LoanRequestDTO request) {
        Loan loan = new Loan();
        loan.setDueDate(request.dueDate());
        return loan;
    }

    @Override
    public LoanResponseDTO toResponseDTO(Loan loan) {
        return new LoanResponseDTO(
                loan.getId(),
                loan.getLender().getId(),
                loan.getBorrower().getId(),
                loan.getBookCopy().getId(),
                loan.getStatus().name(),
                loan.getRequestDate(),
                loan.getApprovalDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                null // links attached by the controller (needs request context for absolute URLs)
        );
    }
}
package com.readshelf.system;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a failed @Valid @RequestBody into a structured field -> message response
 * instead of Spring's default error blob. Phase 8 will generalize this into the
 * full RFC 7807 problem-detail handler.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
            var errorsList = ex.getBindingResult().getFieldErrors();
            var globalErrorsList = ex.getGlobalErrors();
            for(var error: errorsList) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
            for(var error: globalErrorsList) {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        return errors;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Conflict");
        error.put("message", "The record you are trying to update was modified by another user. Please reload and try again.");
        return error;
    }
}
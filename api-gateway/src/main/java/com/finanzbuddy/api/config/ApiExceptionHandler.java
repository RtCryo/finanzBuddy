package com.finanzbuddy.api.config;

import com.finanzbuddy.api.auth.domain.exception.EmailAlreadyRegisteredException;
import com.finanzbuddy.api.auth.domain.exception.InvalidCredentialsException;
import com.finanzbuddy.api.document.domain.exception.ConcurrentDocumentModificationException;
import com.finanzbuddy.api.document.domain.exception.DocumentNotFoundException;
import com.finanzbuddy.api.document.domain.exception.DownloadUnavailableException;
import com.finanzbuddy.api.document.domain.exception.InvalidDocumentRequestException;
import com.finanzbuddy.api.document.domain.exception.UnauthorizedDocumentAccessException;
import com.finanzbuddy.api.document.domain.exception.UploadFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        String detail = exception.getReason();
        if (detail == null || detail.isBlank()) {
            detail = exception.getStatusCode().toString();
        }
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleDocumentNotFound(DocumentNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedDocumentAccessException.class)
    public ProblemDetail handleUnauthorizedDocumentAccess(
            UnauthorizedDocumentAccessException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(InvalidDocumentRequestException.class)
    public ProblemDetail handleInvalidDocumentRequest(InvalidDocumentRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ConcurrentDocumentModificationException.class)
    public ProblemDetail handleConcurrentModification(
            ConcurrentDocumentModificationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(UploadFailedException.class)
    public ProblemDetail handleUploadFailed(UploadFailedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(DownloadUnavailableException.class)
    public ProblemDetail handleDownloadUnavailable(DownloadUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }
}

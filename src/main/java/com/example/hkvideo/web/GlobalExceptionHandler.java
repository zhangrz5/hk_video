package com.example.hkvideo.web;

import com.example.hkvideo.hikvision.HikvisionException;
import com.example.hkvideo.web.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HikvisionException.class)
    ResponseEntity<ApiErrorResponse> handleHikvision(HikvisionException ex) {
        String code = ex.getCode() == null ? "HIKVISION_ERROR" : ex.getCode();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse(code, ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    void handleClientDisconnect(Exception ex) {
        // The browser, curl, or Nginx closed a streaming response. The FLV response
        // may already be committed, so do not try to write a JSON error body.
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", ex.getMessage(), Instant.now()));
    }
}

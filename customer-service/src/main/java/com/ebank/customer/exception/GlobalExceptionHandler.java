package com.ebank.customer.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CustomerNotFoundException exception,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(Instant.now(),
                404, "Not Found", exception.getMessage(), request.getRequestURI(), Map.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return badRequest("Validation failed", fields, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequest("Request body must contain valid customer JSON", Map.of(), request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequest("Invalid request parameter type", Map.of(), request, headers);
    }

    private ResponseEntity<Object> badRequest(String message, Map<String, String> fields,
                                             WebRequest request, HttpHeaders headers) {
        var path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ResponseEntity<>(new ApiError(Instant.now(), 400, "Bad Request", message, path, fields),
                headers, HttpStatus.BAD_REQUEST);
    }
}

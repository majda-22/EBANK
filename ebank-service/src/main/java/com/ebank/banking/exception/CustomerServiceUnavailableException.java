package com.ebank.banking.exception;

public class CustomerServiceUnavailableException extends RuntimeException {
    public CustomerServiceUnavailableException(Throwable cause) {
        super("Customer service is temporarily unavailable; please try again later", cause);
    }
}

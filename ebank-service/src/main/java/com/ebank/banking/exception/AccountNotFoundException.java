package com.ebank.banking.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String id) {
        super("Bank account with id " + id + " was not found");
    }
}

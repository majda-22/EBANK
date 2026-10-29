package com.ebank.banking.model;

/** A fallback value for display, never proof that a customer exists. */
public final class UnavailableCustomer extends Customer {
    public UnavailableCustomer(Long id) {
        super(id, "Not available", "Not available", "Not available");
    }
}

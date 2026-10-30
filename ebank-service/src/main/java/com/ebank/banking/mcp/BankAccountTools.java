package com.ebank.banking.mcp;

import java.util.List;
import java.util.stream.Collectors;
import com.ebank.banking.dto.BankAccountDTO;
import com.ebank.banking.dto.CreateBankAccountRequest;
import com.ebank.banking.enums.AccountType;
import com.ebank.banking.service.BankAccountService;
import com.ebank.banking.service.CustomerLookupService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BankAccountTools {
    private final BankAccountService service;
    private final CustomerLookupService customers;
    private final Validator validator;

    @Tool(description = "List all bank accounts with id (UUID string), balance, createdAt (UTC instant), type, customerId, and nested customer details. Returns an empty list when there are no accounts. Customer text fields are 'Not available' during a customer-service outage. This operation does not change data.")
    public List<BankAccountDTO> getAllAccounts() {
        return service.findAll();
    }

    @Tool(description = "Get one bank account by its UUID string, including balance, creation time, type, customerId, and nested customer details. Reports 'Bank account with id ... was not found' when absent. Customer text fields may be 'Not available' during a customer-service outage. This operation does not change data.")
    public BankAccountDTO getAccountById(
            @ToolParam(description = "Existing bank account UUID string returned by getAllAccounts or createAccount; do not supply a numeric customer ID.") String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID must be a nonblank UUID string");
        }
        return service.findById(accountId);
    }

    @Tool(description = "List all accounts belonging to one existing customer, including nested customer details. Returns an empty list when the customer exists but has no accounts. Reports a clear customer-not-found error for an unknown customer ID; verification requires customer-service to be available. This operation does not change data.")
    public List<BankAccountDTO> getAccountsByCustomer(
            @ToolParam(description = "Existing customer ID: a positive integer returned by the customer tools, not an account UUID.") Long customerId) {
        requirePositiveId(customerId);
        customers.requireExistingCustomer(customerId);
        return service.findByCustomerId(customerId);
    }

    @Tool(description = "Create and persist a bank account for an existing customer. Returns an account DTO with generated UUID, UTC creation time, initial balance, type, customerId, and nested customer details. Rejects unknown customers or unavailable customer verification. This operation changes data; it does not create a customer.")
    public BankAccountDTO createAccount(
            @ToolParam(description = "Initial account balance as a finite, nonnegative number; for example 1500.50. No currency conversion is performed.") Double balance,
            @ToolParam(description = "Account type: exactly CURRENT_ACCOUNT for a current account or SAVING_ACCOUNT for a savings account.") AccountType type,
            @ToolParam(description = "Existing customer's positive numeric ID; obtain it from customer tools before creating the account.") Long customerId) {
        var request = new CreateBankAccountRequest(balance, type, customerId);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid account: " + violations.stream()
                    .map(error -> error.getPropertyPath() + " " + error.getMessage())
                    .sorted().collect(Collectors.joining("; ")));
        }
        if (!Double.isFinite(balance)) {
            throw new IllegalArgumentException("Initial balance must be a finite number");
        }
        return service.create(request);
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Customer ID must be a positive integer");
        }
    }
}

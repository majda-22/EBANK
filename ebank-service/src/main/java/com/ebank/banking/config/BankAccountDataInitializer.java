package com.ebank.banking.config;

import java.time.Instant;
import java.util.Arrays;
import com.ebank.banking.entity.BankAccount;
import com.ebank.banking.enums.AccountType;
import com.ebank.banking.exception.CustomerServiceUnavailableException;
import com.ebank.banking.repository.BankAccountRepository;
import com.ebank.banking.service.CustomerLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankAccountDataInitializer implements CommandLineRunner {
    private final CustomerLookupService customers;
    private final BankAccountRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            for (var customer : customers.allCustomers()) {
                var existingTypes = repository.findByCustomerId(customer.getId()).stream()
                        .map(BankAccount::getType).toList();
                var missing = Arrays.stream(AccountType.values()).filter(type -> !existingTypes.contains(type))
                        .map(type -> new BankAccount(null,
                                type == AccountType.CURRENT_ACCOUNT ? 1000.0 : 2500.0,
                                Instant.now(), type, customer.getId(), null)).toList();
                repository.saveAll(missing);
            }
        } catch (CustomerServiceUnavailableException exception) {
            log.warn("Skipping account seed: customer-service is unavailable. Restart ebank-service to seed later.");
        }
    }
}

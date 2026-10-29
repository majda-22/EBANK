package com.ebank.banking.dto;

import java.time.Instant;
import com.ebank.banking.enums.AccountType;
import com.ebank.banking.model.Customer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountDTO {
    private String id;
    private double balance;
    private Instant createdAt;
    private AccountType type;
    private Long customerId;
    private Customer customer;
}

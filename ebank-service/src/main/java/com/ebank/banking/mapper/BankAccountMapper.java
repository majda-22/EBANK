package com.ebank.banking.mapper;

import java.time.Instant;
import com.ebank.banking.dto.BankAccountDTO;
import com.ebank.banking.dto.CreateBankAccountRequest;
import com.ebank.banking.entity.BankAccount;
import org.springframework.stereotype.Component;

@Component
public class BankAccountMapper {
    public BankAccountDTO toDTO(BankAccount account) {
        return new BankAccountDTO(account.getId(), account.getBalance(), account.getCreatedAt(),
                account.getType(), account.getCustomerId(), account.getCustomer());
    }

    public BankAccount toEntity(CreateBankAccountRequest request) {
        return new BankAccount(null, request.getBalance(), Instant.now(),
                request.getType(), request.getCustomerId(), null);
    }
}

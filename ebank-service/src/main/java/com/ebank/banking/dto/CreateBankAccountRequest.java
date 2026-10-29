package com.ebank.banking.dto;

import com.ebank.banking.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBankAccountRequest {
    @NotNull
    @DecimalMin("0.0")
    private Double balance;

    @NotNull
    private AccountType type;

    @NotNull
    @Positive
    private Long customerId;
}

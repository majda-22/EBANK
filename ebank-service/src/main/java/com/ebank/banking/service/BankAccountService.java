package com.ebank.banking.service;

import java.util.List;
import com.ebank.banking.dto.BankAccountDTO;
import com.ebank.banking.dto.CreateBankAccountRequest;

public interface BankAccountService {
    List<BankAccountDTO> findAll();
    BankAccountDTO findById(String id);
    List<BankAccountDTO> findByCustomerId(Long customerId);
    BankAccountDTO create(CreateBankAccountRequest request);
}

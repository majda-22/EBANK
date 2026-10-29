package com.ebank.banking.service.impl;

import java.util.HashMap;
import java.util.List;
import com.ebank.banking.dto.BankAccountDTO;
import com.ebank.banking.dto.CreateBankAccountRequest;
import com.ebank.banking.entity.BankAccount;
import com.ebank.banking.exception.AccountNotFoundException;
import com.ebank.banking.mapper.BankAccountMapper;
import com.ebank.banking.model.Customer;
import com.ebank.banking.repository.BankAccountRepository;
import com.ebank.banking.service.BankAccountService;
import com.ebank.banking.service.CustomerLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountServiceImpl implements BankAccountService {
    private final BankAccountRepository repository;
    private final BankAccountMapper mapper;
    private final CustomerLookupService customers;

    @Override
    public List<BankAccountDTO> findAll() {
        return enrich(repository.findAll(Sort.by("createdAt", "id")));
    }

    @Override
    public BankAccountDTO findById(String id) {
        var account = repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.setCustomer(customers.findCustomerById(account.getCustomerId()));
        return mapper.toDTO(account);
    }

    @Override
    public List<BankAccountDTO> findByCustomerId(Long customerId) {
        return enrich(repository.findByCustomerId(customerId));
    }

    @Override
    @Transactional
    public BankAccountDTO create(CreateBankAccountRequest request) {
        var customer = customers.findCustomerById(request.getCustomerId());
        var account = mapper.toEntity(request);
        account.setCustomer(customer);
        return mapper.toDTO(repository.save(account));
    }

    private List<BankAccountDTO> enrich(List<BankAccount> accounts) {
        var cache = new HashMap<Long, Customer>();
        return accounts.stream().map(account -> {
            account.setCustomer(cache.computeIfAbsent(account.getCustomerId(), customers::findCustomerById));
            return mapper.toDTO(account);
        }).toList();
    }
}

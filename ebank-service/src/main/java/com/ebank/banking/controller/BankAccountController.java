package com.ebank.banking.controller;

import java.util.List;
import com.ebank.banking.dto.BankAccountDTO;
import com.ebank.banking.dto.CreateBankAccountRequest;
import com.ebank.banking.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService service;

    @GetMapping
    public List<BankAccountDTO> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public BankAccountDTO findById(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping("/customer/{customerId}")
    public List<BankAccountDTO> findByCustomerId(@PathVariable Long customerId) {
        return service.findByCustomerId(customerId);
    }

    @PostMapping
    public ResponseEntity<BankAccountDTO> create(@Valid @RequestBody CreateBankAccountRequest request) {
        var account = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(account.getId()).toUri();
        return ResponseEntity.created(location).body(account);
    }
}

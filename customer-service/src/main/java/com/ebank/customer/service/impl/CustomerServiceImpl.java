package com.ebank.customer.service.impl;

import java.util.List;
import com.ebank.customer.dto.CustomerDTO;
import com.ebank.customer.entity.Customer;
import com.ebank.customer.exception.CustomerNotFoundException;
import com.ebank.customer.mapper.CustomerMapper;
import com.ebank.customer.repository.CustomerRepository;
import com.ebank.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    @Override
    public List<CustomerDTO> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toDTO).toList();
    }

    @Override
    public CustomerDTO findById(Long id) {
        return mapper.toDTO(requireCustomer(id));
    }

    @Override
    @Transactional
    public CustomerDTO create(CustomerDTO customer) {
        return mapper.toDTO(repository.save(mapper.toEntity(customer)));
    }

    @Override
    @Transactional
    public CustomerDTO update(Long id, CustomerDTO customer) {
        var entity = requireCustomer(id);
        mapper.updateEntity(customer, entity);
        return mapper.toDTO(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(requireCustomer(id));
    }

    private Customer requireCustomer(Long id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }
}

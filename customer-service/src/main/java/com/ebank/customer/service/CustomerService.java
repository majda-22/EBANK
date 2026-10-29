package com.ebank.customer.service;

import java.util.List;
import com.ebank.customer.dto.CustomerDTO;

public interface CustomerService {
    List<CustomerDTO> findAll();
    CustomerDTO findById(Long id);
    CustomerDTO create(CustomerDTO customer);
    CustomerDTO update(Long id, CustomerDTO customer);
    void delete(Long id);
}

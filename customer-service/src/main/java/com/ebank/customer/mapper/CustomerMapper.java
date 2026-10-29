package com.ebank.customer.mapper;

import com.ebank.customer.dto.CustomerDTO;
import com.ebank.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerDTO toDTO(Customer customer) {
        return new CustomerDTO(customer.getId(), customer.getFirstName(),
                customer.getLastName(), customer.getEmail());
    }

    public Customer toEntity(CustomerDTO dto) {
        return new Customer(null, dto.getFirstName(), dto.getLastName(), dto.getEmail());
    }

    public void updateEntity(CustomerDTO dto, Customer customer) {
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setEmail(dto.getEmail());
    }
}

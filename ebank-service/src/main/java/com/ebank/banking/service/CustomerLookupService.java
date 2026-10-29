package com.ebank.banking.service;

import java.util.List;
import com.ebank.banking.client.CustomerRestClient;
import com.ebank.banking.exception.CustomerServiceUnavailableException;
import com.ebank.banking.model.Customer;
import com.ebank.banking.model.UnavailableCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerLookupService {
    private final CustomerRestClient client;

    public Customer findCustomerById(Long id) {
        return client.findCustomerById(id);
    }

    public Customer requireExistingCustomer(Long id) {
        var customer = findCustomerById(id);
        if (customer instanceof UnavailableCustomer) {
            throw new CustomerServiceUnavailableException(null);
        }
        return customer;
    }

    public List<Customer> allCustomers() {
        return client.allCustomers();
    }
}

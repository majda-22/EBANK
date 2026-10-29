package com.ebank.banking.service;

import java.util.List;
import com.ebank.banking.client.CustomerRestClient;
import com.ebank.banking.exception.CustomerNotFoundException;
import com.ebank.banking.exception.CustomerServiceUnavailableException;
import com.ebank.banking.model.Customer;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerLookupService {
    private final CustomerRestClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public Customer findCustomerById(Long id) {
        return circuitBreakers.create("customer-service").run(() -> client.findCustomerById(id), cause -> {
            if (cause instanceof FeignException.NotFound) {
                throw new CustomerNotFoundException(id);
            }
            throw new CustomerServiceUnavailableException(cause);
        });
    }

    public List<Customer> allCustomers() {
        return circuitBreakers.create("customer-service").run(client::allCustomers, cause -> {
            throw new CustomerServiceUnavailableException(cause);
        });
    }
}

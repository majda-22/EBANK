package com.ebank.banking.client;

import java.util.List;
import com.ebank.banking.exception.CustomerNotFoundException;
import com.ebank.banking.model.Customer;
import com.ebank.banking.model.UnavailableCustomer;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomerRestClientFallbackFactory implements FallbackFactory<CustomerRestClient> {
    @Override
    public CustomerRestClient create(Throwable cause) {
        log.warn("CustomerRestClient fallback: {}", cause.toString(), cause);
        return new CustomerRestClient() {
            @Override
            public Customer findCustomerById(Long id) {
                if (cause instanceof FeignException.NotFound) {
                    throw new CustomerNotFoundException(id);
                }
                return new UnavailableCustomer(id);
            }

            @Override
            public List<Customer> allCustomers() {
                // Never create seed accounts for invented customers during an outage.
                return List.of();
            }
        };
    }
}

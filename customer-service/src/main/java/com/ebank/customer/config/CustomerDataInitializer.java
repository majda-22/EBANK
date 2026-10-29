package com.ebank.customer.config;

import java.util.List;
import com.ebank.customer.entity.Customer;
import com.ebank.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CustomerDataInitializer implements CommandLineRunner {

    private final CustomerRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.saveAll(List.of(
                    new Customer(null, "Amine", "El Mansouri", "amine.elmansouri@example.com"),
                    new Customer(null, "Salma", "Bennani", "salma.bennani@example.com"),
                    new Customer(null, "Youssef", "Alaoui", "youssef.alaoui@example.com"),
                    new Customer(null, "Imane", "Berrada", "imane.berrada@example.com"),
                    new Customer(null, "Omar", "Tazi", "omar.tazi@example.com"),
                    new Customer(null, "Nadia", "Idrissi", "nadia.idrissi@example.com"),
                    new Customer(null, "Mehdi", "Amrani", "mehdi.amrani@example.com"),
                    new Customer(null, "Lina", "Chraibi", "lina.chraibi@example.com")
            ));
        }
    }
}

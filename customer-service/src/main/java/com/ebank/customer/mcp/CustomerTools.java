package com.ebank.customer.mcp;

import java.util.List;
import java.util.stream.Collectors;
import com.ebank.customer.dto.CustomerDTO;
import com.ebank.customer.service.CustomerService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerTools {
    private final CustomerService service;
    private final Validator validator;

    @Tool(description = "List all bank customers. Returns customer DTOs containing id, firstName, lastName, and email; returns an empty list when there are no customers. This operation does not change data.")
    public List<CustomerDTO> getAllCustomers() {
        return service.findAll();
    }

    @Tool(description = "Get one bank customer by their numeric customer ID. Returns id, firstName, lastName, and email. Reports 'Customer with id ... was not found' when the customer does not exist. This operation does not change data.")
    public CustomerDTO getCustomerById(
            @ToolParam(description = "Existing customer ID: a positive integer returned by getAllCustomers or createCustomer; this is not an account UUID.") Long customerId) {
        requirePositiveId(customerId);
        return service.findById(customerId);
    }

    @Tool(description = "Create and persist a new bank customer using their first name, last name, and email address. Returns the created customer DTO with a generated numeric ID. This operation changes data and does not create a bank account.")
    public CustomerDTO createCustomer(
            @ToolParam(description = "Customer's first name; required, nonblank, at most 100 characters.") String firstName,
            @ToolParam(description = "Customer's last name; required, nonblank, at most 100 characters.") String lastName,
            @ToolParam(description = "Customer's email address; required, nonblank, valid email syntax, at most 254 characters.") String email) {
        var request = new CustomerDTO(null, firstName, lastName, email);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid customer: " + violations.stream()
                    .map(error -> error.getPropertyPath() + " " + error.getMessage())
                    .sorted().collect(Collectors.joining("; ")));
        }
        return service.create(request);
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Customer ID must be a positive integer");
        }
    }
}

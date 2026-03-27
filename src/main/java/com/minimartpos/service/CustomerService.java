package com.minimartpos.service;

import com.minimartpos.model.Customer;
import com.minimartpos.repository.CustomerRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

public class CustomerService {

    private static final Logger logger = LogManager.getLogger(CustomerService.class);
    private final CustomerRepository repo         = new CustomerRepository();
    private final AuditService       auditService = new AuditService();

    public List<Customer> getAll()               { return repo.findAll(); }
    public List<Customer> search(String query)   { return repo.search(query); }
    public Optional<Customer> findById(int id)   { return repo.findById(id); }

    public int save(Customer c) {
        if (c.getId() == 0) {
            int id = repo.insert(c);
            auditService.log("CUSTOMER_CREATE", "customers", id, null, "name=" + c.getName());
            return id;
        } else {
            boolean ok = repo.update(c);
            if (ok) auditService.log("CUSTOMER_UPDATE", "customers", c.getId(), null, null);
            return ok ? c.getId() : -1;
        }
    }

    public boolean setActive(int id, boolean active) {
        Optional<Customer> opt = repo.findById(id);
        if (opt.isEmpty()) return false;
        Customer c = opt.get();
        c.setActive(active);
        return repo.update(c);
    }
}

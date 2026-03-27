package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Customer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class CustomerRepository {

    private static final Logger logger = LogManager.getLogger(CustomerRepository.class);

    private static final String SQL_FIND_ALL =
        "SELECT * FROM customers WHERE active=1 ORDER BY name";

    private static final String SQL_SEARCH =
        "SELECT * FROM customers WHERE active=1 AND (name LIKE ? OR phone LIKE ?) " +
        "ORDER BY name LIMIT 30";

    private static final String SQL_INSERT =
        "INSERT INTO customers (name, phone, email, address, credit_limit) VALUES (?,?,?,?,?)";

    private static final String SQL_UPDATE =
        "UPDATE customers SET name=?, phone=?, email=?, address=?, credit_limit=?, " +
        "active=?, notes=?, updated_at=NOW() WHERE id=?";

    public List<Customer> findAll() {
        List<Customer> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("findAll customers: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<Customer> search(String query) {
        List<Customer> list = new ArrayList<>();
        String like = "%" + query + "%";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEARCH)) {
            ps.setString(1, like);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("search customers: {}", e.getMessage(), e);
        }
        return list;
    }

    public Optional<Customer> findById(int id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findById customer: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public int insert(Customer c) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setBigDecimal(5, c.getCreditLimit() != null ? c.getCreditLimit() : BigDecimal.ZERO);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) { int id = keys.getInt(1); c.setId(id); return id; }
            }
        } catch (SQLException e) {
            logger.error("insert customer: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean update(Customer c) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setBigDecimal(5, c.getCreditLimit());
            ps.setBoolean(6, c.isActive());
            ps.setString(7, c.getNotes());
            ps.setInt(8, c.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("update customer: {}", e.getMessage(), e);
        }
        return false;
    }

    public boolean delete(int id) {
        try (var conn = com.minimartpos.config.DatabaseConfig.getConnection();
             var ps   = conn.prepareStatement("DELETE FROM customers WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("delete customer error: {}", e.getMessage());
        }
        return false;
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setCreditLimit(rs.getBigDecimal("credit_limit"));
        c.setCreditBalance(rs.getBigDecimal("credit_balance"));
        c.setLoyaltyPoints(rs.getInt("loyalty_points"));
        c.setActive(rs.getBoolean("active"));
        c.setNotes(rs.getString("notes"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        return c;
    }
}

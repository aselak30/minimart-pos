package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierRepository {

    private static final Logger logger = LogManager.getLogger(SupplierRepository.class);

    public List<Supplier> findAllActive() {
        List<Supplier> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM suppliers WHERE active=1 ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Supplier s = new Supplier();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setContactName(rs.getString("contact_name"));
                s.setPhone(rs.getString("phone"));
                s.setEmail(rs.getString("email"));
                s.setAddress(rs.getString("address"));
                s.setActive(rs.getBoolean("active"));
                list.add(s);
            }
        } catch (SQLException e) {
            logger.error("findAllActive suppliers error: {}", e.getMessage(), e);
        }
        return list;
    }

    public int insert(Supplier s) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO suppliers (name, contact_name, phone, email, address) VALUES (?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactName());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) { int id = keys.getInt(1); s.setId(id); return id; }
            }
        } catch (SQLException e) {
            logger.error("insert supplier error: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean update(Supplier s) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE suppliers SET name=?, contact_name=?, phone=?, email=?, " +
                 "address=?, active=?, updated_at=NOW() WHERE id=?")) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactName());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setBoolean(6, s.isActive());
            ps.setInt(7, s.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("update supplier error: {}", e.getMessage(), e);
        }
        return false;
    }

    public boolean delete(int id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM suppliers WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("delete supplier error: {}", e.getMessage(), e);
        }
        return false;
    }
}

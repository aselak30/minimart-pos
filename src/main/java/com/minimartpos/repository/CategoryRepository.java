package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Category;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryRepository {

    private static final Logger logger = LogManager.getLogger(CategoryRepository.class);

    public List<Category> findAllActive() {
        List<Category> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM categories WHERE active=1 ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Category c = new Category();
                c.setId(rs.getInt("id"));
                c.setName(rs.getString("name"));
                c.setActive(rs.getBoolean("active"));
                list.add(c);
            }
        } catch (SQLException e) {
            logger.error("findAllActive categories error: {}", e.getMessage(), e);
        }
        return list;
    }

    public int insert(String name) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO categories (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("insert category error: {}", e.getMessage(), e);
        }
        return -1;
    }
}

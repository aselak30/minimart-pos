package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Product;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access object for the products table.
 */
public class ProductRepository {

    private static final Logger logger = LogManager.getLogger(ProductRepository.class);

    private static final String SQL_FIND_ALL_ACTIVE =
        "SELECT p.*, c.name AS category_name, s.name AS supplier_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "LEFT JOIN suppliers  s ON p.supplier_id  = s.id " +
        "WHERE p.active = 1 ORDER BY p.name";

    private static final String SQL_FIND_BY_ID =
        "SELECT p.*, c.name AS category_name, s.name AS supplier_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "LEFT JOIN suppliers  s ON p.supplier_id  = s.id " +
        "WHERE p.id = ? LIMIT 1";

    private static final String SQL_FIND_BY_BARCODE =
        "SELECT p.*, c.name AS category_name, s.name AS supplier_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "LEFT JOIN suppliers  s ON p.supplier_id  = s.id " +
        "WHERE p.barcode = ? AND p.active = 1 LIMIT 1";

    private static final String SQL_SEARCH =
        "SELECT p.*, c.name AS category_name, s.name AS supplier_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "LEFT JOIN suppliers  s ON p.supplier_id  = s.id " +
        "WHERE p.active = 1 AND (p.name LIKE ? OR p.barcode LIKE ? OR p.brand LIKE ?) " +
        "ORDER BY p.name LIMIT 100";

    private static final String SQL_LOW_STOCK =
        "SELECT p.*, c.name AS category_name, s.name AS supplier_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "LEFT JOIN suppliers  s ON p.supplier_id  = s.id " +
        "WHERE p.active = 1 AND p.stock_quantity <= p.reorder_level " +
        "ORDER BY p.stock_quantity ASC";

    private static final String SQL_INSERT =
        "INSERT INTO products (barcode, name, category_id, brand, size_weight, unit_price, cost_price, " +
        "tax_rate, discount_allowed, max_discount_percent, stock_quantity, reorder_level, " +
        "expiry_date, batch_number, supplier_id, location, active, image_path, description) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_UPDATE =
        "UPDATE products SET barcode=?, name=?, category_id=?, brand=?, size_weight=?, unit_price=?, " +
        "cost_price=?, tax_rate=?, discount_allowed=?, max_discount_percent=?, reorder_level=?, " +
        "expiry_date=?, batch_number=?, supplier_id=?, location=?, active=?, image_path=?, " +
        "description=?, updated_at=NOW() WHERE id=?";

    private static final String SQL_UPDATE_STOCK =
        "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at=NOW() WHERE id=?";

    private static final String SQL_SET_STOCK =
        "UPDATE products SET stock_quantity = ?, updated_at=NOW() WHERE id=?";

    // ── Public Methods ────────────────────────────────────────────────────────

    public List<Product> findAllActive() {
        return query(SQL_FIND_ALL_ACTIVE);
    }

    public Optional<Product> findById(int id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findById error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<Product> findByBarcode(String barcode) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_BARCODE)) {
            ps.setString(1, barcode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findByBarcode error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Product> search(String query) {
        String like = "%" + query + "%";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEARCH)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<Product> results = new ArrayList<>();
                while (rs.next()) results.add(mapRow(rs));
                return results;
            }
        } catch (SQLException e) {
            logger.error("search error: {}", e.getMessage(), e);
        }
        return List.of();
    }

    public List<Product> findLowStock() {
        return query(SQL_LOW_STOCK);
    }

    public int insert(Product p) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            setInsertParams(ps, p);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    p.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("insert product error: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean update(Product p) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            setUpdateParams(ps, p);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("update product error: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Adjusts stock by delta (positive = add, negative = subtract).
     * Uses atomic SQL to avoid race conditions in multi-machine env.
     */
    public boolean adjustStock(int productId, int delta) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STOCK)) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("adjustStock error: {}", e.getMessage(), e);
        }
        return false;
    }

    public boolean setStock(int productId, int quantity) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SET_STOCK)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("setStock error: {}", e.getMessage(), e);
        }
        return false;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<Product> query(String sql) {
        List<Product> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("query error [{}]: {}", sql, e.getMessage(), e);
        }
        return list;
    }

    private void setInsertParams(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1,  p.getBarcode());
        ps.setString(2,  p.getName());
        ps.setInt(3,     p.getCategoryId());
        ps.setString(4,  p.getBrand());
        ps.setString(5,  p.getSizeWeight());
        ps.setBigDecimal(6,  p.getUnitPrice());
        ps.setBigDecimal(7,  p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
        ps.setBigDecimal(8,  p.getTaxRate());
        ps.setBoolean(9,     p.isDiscountAllowed());
        ps.setBigDecimal(10, p.getMaxDiscountPercent());
        ps.setInt(11,    p.getStockQuantity());
        ps.setInt(12,    p.getReorderLevel());
        ps.setDate(13,   p.getExpiryDate() != null ? Date.valueOf(p.getExpiryDate()) : null);
        ps.setString(14, p.getBatchNumber());
        if (p.getSupplierId() > 0) ps.setInt(15, p.getSupplierId()); else ps.setNull(15, Types.INTEGER);
        ps.setString(16, p.getLocation());
        ps.setBoolean(17, p.isActive());
        ps.setString(18, p.getImagePath());
        ps.setString(19, p.getDescription());
    }

    private void setUpdateParams(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1,  p.getBarcode());
        ps.setString(2,  p.getName());
        ps.setInt(3,     p.getCategoryId());
        ps.setString(4,  p.getBrand());
        ps.setString(5,  p.getSizeWeight());
        ps.setBigDecimal(6,  p.getUnitPrice());
        ps.setBigDecimal(7,  p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
        ps.setBigDecimal(8,  p.getTaxRate());
        ps.setBoolean(9,     p.isDiscountAllowed());
        ps.setBigDecimal(10, p.getMaxDiscountPercent());
        ps.setInt(11,    p.getReorderLevel());
        ps.setDate(12,   p.getExpiryDate() != null ? Date.valueOf(p.getExpiryDate()) : null);
        ps.setString(13, p.getBatchNumber());
        if (p.getSupplierId() > 0) ps.setInt(14, p.getSupplierId()); else ps.setNull(14, Types.INTEGER);
        ps.setString(15, p.getLocation());
        ps.setBoolean(16, p.isActive());
        ps.setString(17, p.getImagePath());
        ps.setString(18, p.getDescription());
        ps.setInt(19,    p.getId());
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setBarcode(rs.getString("barcode"));
        p.setName(rs.getString("name"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setBrand(rs.getString("brand"));
        p.setSizeWeight(rs.getString("size_weight"));
        p.setUnitPrice(rs.getBigDecimal("unit_price"));
        p.setCostPrice(rs.getBigDecimal("cost_price"));
        p.setTaxRate(rs.getBigDecimal("tax_rate"));
        p.setDiscountAllowed(rs.getBoolean("discount_allowed"));
        p.setMaxDiscountPercent(rs.getBigDecimal("max_discount_percent"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setReorderLevel(rs.getInt("reorder_level"));
        p.setActive(rs.getBoolean("active"));
        p.setImagePath(rs.getString("image_path"));
        p.setDescription(rs.getString("description"));
        p.setLocation(rs.getString("location"));
        p.setBatchNumber(rs.getString("batch_number"));
        p.setSupplierName(rs.getString("supplier_name"));

        Date expiry = rs.getDate("expiry_date");
        if (expiry != null) p.setExpiryDate(expiry.toLocalDate());

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) p.setCreatedAt(created.toLocalDateTime());

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) p.setUpdatedAt(updated.toLocalDateTime());

        return p;
    }
}

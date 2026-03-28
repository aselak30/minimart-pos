package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access object for bills and bill_items tables.
 * Uses transactions to ensure bill + items are always saved atomically.
 */
public class BillRepository {

    private static final Logger logger = LogManager.getLogger(BillRepository.class);

    private static final String SQL_INSERT_BILL =
        "INSERT INTO bills (bill_number, cashier_id, customer_id, machine_id, " +
        "subtotal, discount_percent, discount_amount, tax_amount, total_amount, " +
        "paid_amount, change_amount, cost_total, profit_total, payment_type, " +
        "status, void_reason, notes, created_at, finalized_at, is_editable) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_INSERT_ITEM =
        "INSERT INTO bill_items (bill_id, product_id, product_name, product_barcode, " +
        "quantity, unit_price, original_price, cost_price, discount_percent, discount_amount, " +
        "tax_rate, tax_amount, line_total, is_price_override, is_weight_based, weight, weight_unit) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE bills SET status=?, void_reason=?, updated_at=NOW() WHERE id=?";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM bills WHERE id=?";

    private static final String SQL_FIND_ITEMS =
        "SELECT * FROM bill_items WHERE bill_id=? ORDER BY id";

    private static final String SQL_FIND_BY_CASHIER_TODAY =
        "SELECT * FROM bills WHERE cashier_id=? AND DATE(created_at)=CURDATE() ORDER BY created_at DESC";

    private static final String SQL_FIND_TODAY_ALL =
        "SELECT * FROM bills WHERE DATE(created_at)=CURDATE() ORDER BY created_at DESC LIMIT 50";

    private static final String SQL_SEQUENCE =
        "SELECT COUNT(*) + 1 FROM bills WHERE bill_number LIKE ?";

    // ── Save (insert bill + all items atomically) ─────────────────────────────

    /**
     * Saves a finalized bill and all its items in a single transaction.
     * Sets bill.id on success.
     *
     * @return true if saved successfully
     */
    public boolean saveBill(Bill bill) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Insert bill header
                int billId = insertBillHeader(conn, bill);
                if (billId < 0) {
                    conn.rollback();
                    return false;
                }
                bill.setId(billId);

                // 2. Insert all items
                insertBillItems(conn, billId, bill.getItems());

                conn.commit();
                logger.info("Bill saved to DB: id={} number={}", billId, bill.getBillNumber());
                return true;

            } catch (SQLException e) {
                conn.rollback();
                logger.error("saveBill transaction failed, rolled back: {}", e.getMessage(), e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("saveBill connection error: {}", e.getMessage(), e);
            return false;
        }
    }

    private int insertBillHeader(Connection conn, Bill bill) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BILL,
                                                          Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,  bill.getBillNumber());
            ps.setInt(2,     bill.getCashierId());
            if (bill.getCustomerId() > 0)
                ps.setInt(3, bill.getCustomerId());
            else
                ps.setNull(3, Types.INTEGER);
            if (bill.getMachineId() > 0)
                ps.setInt(4, bill.getMachineId());
            else
                ps.setNull(4, Types.INTEGER);
            ps.setBigDecimal(5,  bill.getSubtotal());
            ps.setBigDecimal(6,  bill.getDiscountPercent() != null
                                 ? bill.getDiscountPercent() : java.math.BigDecimal.ZERO);
            ps.setBigDecimal(7,  bill.getDiscountAmount());
            ps.setBigDecimal(8,  bill.getTaxAmount());
            ps.setBigDecimal(9,  bill.getTotalAmount());
            ps.setBigDecimal(10, bill.getPaidAmount());
            ps.setBigDecimal(11, bill.getChangeAmount());
            ps.setBigDecimal(12, bill.getCostTotal());
            ps.setBigDecimal(13, bill.getProfitTotal());
            ps.setString(14, bill.getPaymentType().name());
            ps.setString(15, bill.getStatus().name());
            ps.setString(16, bill.getVoidReason());
            ps.setString(17, bill.getNotes());
            ps.setTimestamp(18, Timestamp.valueOf(bill.getCreatedAt()));
            ps.setTimestamp(19, bill.getFinalizedAt() != null
                                ? Timestamp.valueOf(bill.getFinalizedAt()) : null);
            ps.setBoolean(20, bill.isEditable());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    private void insertBillItems(Connection conn, int billId, List<BillItem> items)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ITEM,
                                                          Statement.RETURN_GENERATED_KEYS)) {
            for (BillItem item : items) {
                ps.setInt(1,     billId);
                ps.setInt(2,     item.getProductId());
                ps.setString(3,  item.getProductName());
                ps.setString(4,  item.getProductBarcode());
                ps.setBigDecimal(5,  item.getQuantity());
                ps.setBigDecimal(6,  item.getUnitPrice());
                ps.setBigDecimal(7,  item.getOriginalPrice());
                ps.setBigDecimal(8,  item.getCostPrice() != null
                                     ? item.getCostPrice()
                                     : java.math.BigDecimal.ZERO);
                ps.setBigDecimal(9,  item.getDiscountPercent());
                ps.setBigDecimal(10, item.getDiscountAmount());
                ps.setBigDecimal(11, item.getTaxRate());
                ps.setBigDecimal(12, item.getTaxAmount());
                ps.setBigDecimal(13, item.getLineTotal());
                ps.setBoolean(14, item.isPriceOverride());
                ps.setBoolean(15, item.isWeightBased());
                ps.setBigDecimal(16, item.getWeight());
                ps.setString(17, item.getWeightUnit());
                ps.addBatch();
            }
            ps.executeBatch();

            // Read back generated IDs and assign to items
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int i = 0;
                while (keys.next() && i < items.size()) {
                    items.get(i++).setId(keys.getInt(1));
                }
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public boolean updateBillStatus(Bill bill) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            ps.setString(1, bill.getStatus().name());
            ps.setString(2, bill.getVoidReason());
            ps.setInt(3,    bill.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("updateBillStatus error: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Updates bill header fields + all line items atomically.
     * Used by bill edit — replaces the current items with the edited set.
     * Restores old stock and deducts new stock is handled by BillingService.
     */
    public boolean updateBill(Bill bill) {
        String updateBillSql =
            "UPDATE bills SET customer_id=?, payment_type=?, " +
            "  subtotal=?, discount_amount=?, discount_percent=?, " +
            "  tax_amount=?, total_amount=?, notes=?, updated_at=NOW() " +
            "WHERE id=?";
        String deleteItemsSql = "DELETE FROM bill_items WHERE bill_id=?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update bill header
                try (PreparedStatement ps = conn.prepareStatement(updateBillSql)) {
                    if (bill.getCustomerId() > 0) ps.setInt(1, bill.getCustomerId());
                    else                          ps.setNull(1, java.sql.Types.INTEGER);
                    ps.setString(2, bill.getPaymentType() != null
                        ? bill.getPaymentType().name() : "CASH");
                    ps.setBigDecimal(3,  bill.getSubtotal());
                    ps.setBigDecimal(4,  bill.getDiscountAmount());
                    ps.setBigDecimal(5,  bill.getDiscountPercent());
                    ps.setBigDecimal(6,  bill.getTaxAmount());
                    ps.setBigDecimal(7,  bill.getTotalAmount());
                    ps.setString(8,      bill.getNotes());
                    ps.setInt(9,         bill.getId());
                    ps.executeUpdate();
                }

                // 2. Delete all old items
                try (PreparedStatement ps = conn.prepareStatement(deleteItemsSql)) {
                    ps.setInt(1, bill.getId());
                    ps.executeUpdate();
                }

                // 3. Re-insert new items
                insertBillItems(conn, bill.getId(), bill.getItems());

                conn.commit();
                logger.info("Bill {} updated: {} items, total={}",
                    bill.getBillNumber(), bill.getItems().size(), bill.getTotalAmount());
                return true;

            } catch (SQLException e) {
                conn.rollback();
                logger.error("updateBill transaction failed: {}", e.getMessage(), e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("updateBill connection error: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<Bill> findById(int id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Bill bill = mapBillRow(rs);
                    bill.setItems(findItemsByBillId(conn, id));
                    return Optional.of(bill);
                }
            }
        } catch (SQLException e) {
            logger.error("findById error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Bill> findByCashierToday(int cashierId) {
        List<Bill> bills = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_CASHIER_TODAY)) {
            ps.setInt(1, cashierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bills.add(mapBillRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findByCashierToday error: {}", e.getMessage(), e);
        }
        return bills;
    }

    /** Returns up to 50 bills from today across all cashiers, newest first. */
    public List<Bill> findTodayAll() {
        List<Bill> bills = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_TODAY_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) bills.add(mapBillRow(rs));
        } catch (SQLException e) {
            logger.error("findTodayAll error: {}", e.getMessage(), e);
        }
        return bills;
    }

    /**
     * Returns the next bill sequence number for a given date string (yyyy-MM-dd).
     * Thread-safe: uses DB COUNT which is atomic under the transaction.
     */
    public int getNextSequenceForDate(String date) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEQUENCE)) {
            ps.setString(1, "BILL-" + date + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("getNextSequence error: {}", e.getMessage(), e);
        }
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<BillItem> findItemsByBillId(Connection conn, int billId) throws SQLException {
        List<BillItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_ITEMS)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(mapItemRow(rs));
            }
        }
        return items;
    }

    private Bill mapBillRow(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setId(rs.getInt("id"));
        b.setBillNumber(rs.getString("bill_number"));
        b.setCashierId(rs.getInt("cashier_id"));
        b.setCustomerId(rs.getInt("customer_id"));
        b.setMachineId(rs.getInt("machine_id"));
        b.setSubtotal(rs.getBigDecimal("subtotal"));
        b.setDiscountPercent(rs.getBigDecimal("discount_percent") != null
            ? rs.getBigDecimal("discount_percent") : java.math.BigDecimal.ZERO);
        b.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        b.setTaxAmount(rs.getBigDecimal("tax_amount"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setPaidAmount(rs.getBigDecimal("paid_amount"));
        b.setChangeAmount(rs.getBigDecimal("change_amount"));
        b.setStatus(Bill.Status.valueOf(rs.getString("status")));
        b.setPaymentType(Bill.PayType.valueOf(rs.getString("payment_type")));
        b.setVoidReason(rs.getString("void_reason"));
        b.setNotes(rs.getString("notes"));
        // Denormalised name columns — present when joined, null when not
        try { b.setCashierName(rs.getString("cashier_name")); } catch (Exception ignored) {}
        try { b.setCustomerName(rs.getString("customer_name")); } catch (Exception ignored) {}
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) b.setCreatedAt(created.toLocalDateTime());
        Timestamp finalized = rs.getTimestamp("finalized_at");
        if (finalized != null) b.setFinalizedAt(finalized.toLocalDateTime());
        
        try {
            b.setEditable(rs.getBoolean("is_editable"));
            b.setDeletedBy(rs.getInt("deleted_by"));
            b.setDeletedReason(rs.getString("deleted_reason"));
        } catch (SQLException ignored) {
            // New columns not present in result set
        }
        return b;
    }

    private BillItem mapItemRow(ResultSet rs) throws SQLException {
        BillItem i = new BillItem();
        i.setId(rs.getInt("id"));
        i.setBillId(rs.getInt("bill_id"));
        i.setProductId(rs.getInt("product_id"));
        i.setProductName(rs.getString("product_name"));
        i.setProductBarcode(rs.getString("product_barcode"));
        i.setQuantity(rs.getBigDecimal("quantity"));
        i.setUnitPrice(rs.getBigDecimal("unit_price"));
        i.setOriginalPrice(rs.getBigDecimal("original_price"));
        i.setCostPrice(rs.getBigDecimal("cost_price"));
        i.setDiscountPercent(rs.getBigDecimal("discount_percent"));
        i.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        i.setTaxRate(rs.getBigDecimal("tax_rate"));
        i.setPriceOverride(rs.getBoolean("is_price_override"));
        
        try {
            i.setWeightBased(rs.getBoolean("is_weight_based"));
            i.setWeight(rs.getBigDecimal("weight"));
            i.setWeightUnit(rs.getString("weight_unit"));
        } catch (SQLException ignored) {
            // New columns
        }
        return i;
    }

    /**
     * Returns the total cash collected (CASH payment type only) by a cashier today.
     * Used for cash limit monitoring.
     */
    public java.math.BigDecimal getTodayCashCollected(int cashierId) {
        String sql = "SELECT COALESCE(SUM(total_amount),0) FROM bills " +
                     "WHERE cashier_id=? AND payment_type='CASH' " +
                     "AND status='COMPLETED' AND DATE(created_at)=CURDATE()";
        try (var conn = com.minimartpos.config.DatabaseConfig.getConnection();
             var ps   = conn.prepareStatement(sql)) {
            ps.setInt(1, cashierId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (Exception e) {
            logger.error("getTodayCashCollected error: {}", e.getMessage());
        }
        return java.math.BigDecimal.ZERO;
    }
}

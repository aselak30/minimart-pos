package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Shift;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.*;

public class ShiftRepository {

    private static final Logger logger = LogManager.getLogger(ShiftRepository.class);

    public int openShift(int cashierId, int machineId, java.math.BigDecimal openingCash) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO shifts (cashier_id, machine_id, opening_cash, status) VALUES (?,?,?,'OPEN')",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cashierId);
            ps.setInt(2, machineId);
            ps.setBigDecimal(3, openingCash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("openShift error: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean closeShift(int shiftId, java.math.BigDecimal closingCash) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE shifts SET status='CLOSED', end_time=NOW(), closing_cash=? WHERE id=?")) {
            ps.setBigDecimal(1, closingCash);
            ps.setInt(2, shiftId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("closeShift error: {}", e.getMessage(), e);
        }
        return false;
    }

    public Optional<Shift> findOpenShiftForCashier(int cashierId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT s.*, u.full_name AS cashier_name, m.machine_name " +
                 "FROM shifts s LEFT JOIN users u ON s.cashier_id=u.id " +
                 "LEFT JOIN machines m ON s.machine_id=m.id " +
                 "WHERE s.cashier_id=? AND s.status='OPEN' ORDER BY s.start_time DESC LIMIT 1")) {
            ps.setInt(1, cashierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findOpenShift error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Shift> findByDateRange(java.time.LocalDate from, java.time.LocalDate to) {
        List<Shift> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT s.*, u.full_name AS cashier_name, m.machine_name " +
                 "FROM shifts s LEFT JOIN users u ON s.cashier_id=u.id " +
                 "LEFT JOIN machines m ON s.machine_id=m.id " +
                 "WHERE DATE(s.start_time) BETWEEN ? AND ? ORDER BY s.start_time DESC")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findByDateRange shifts: {}", e.getMessage(), e);
        }
        return list;
    }

    private Shift mapRow(ResultSet rs) throws SQLException {
        Shift s = new Shift();
        s.setId(rs.getInt("id"));
        s.setCashierId(rs.getInt("cashier_id"));
        s.setCashierName(rs.getString("cashier_name"));
        s.setMachineId(rs.getInt("machine_id"));
        s.setMachineName(rs.getString("machine_name"));
        s.setStatus(Shift.Status.valueOf(rs.getString("status")));
        s.setOpeningCash(rs.getBigDecimal("opening_cash"));
        s.setClosingCash(rs.getBigDecimal("closing_cash"));
        s.setTotalSales(rs.getBigDecimal("total_sales"));
        s.setTotalBills(rs.getInt("total_bills"));
        s.setNotes(rs.getString("notes"));
        Timestamp start = rs.getTimestamp("start_time");
        if (start != null) s.setStartTime(start.toLocalDateTime());
        Timestamp end = rs.getTimestamp("end_time");
        if (end != null) s.setEndTime(end.toLocalDateTime());
        return s;
    }
}

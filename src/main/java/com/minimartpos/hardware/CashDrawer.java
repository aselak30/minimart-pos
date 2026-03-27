package com.minimartpos.hardware;

import com.minimartpos.model.enums.Permission;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AuditService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controls the cash drawer hardware.
 *
 * Most retail cash drawers connect via:
 *  - The receipt printer's RJ11/RJ12 port (ESC/POS kick command)
 *  - A serial/USB port directly
 *
 * This class uses the PrinterManager's ESC/POS kick command,
 * which is the most common setup for mini-supermarket setups.
 *
 * Usage:
 *   CashDrawer.open();              // with permission check
 *   CashDrawer.openForced();        // no permission check (e.g. after payment)
 */
public final class CashDrawer {

    private static final Logger logger = LogManager.getLogger(CashDrawer.class);

    private static final PrinterManager printerManager = new PrinterManager();
    private static final AuditService   auditService   = new AuditService();

    private CashDrawer() {}

    /**
     * Opens the cash drawer. Requires OPEN_CASH_DRAWER permission
     * (or no permission check if called after a CASH payment — use openForced()).
     *
     * @return true if drawer opened successfully
     */
    public static boolean open() {
        if (!SessionManager.hasPermission(Permission.OPEN_CASH_DRAWER)) {
            logger.warn("Cash drawer open denied: user {} lacks permission",
                        SessionManager.getCurrentUser().getUsername());
            return false;
        }
        return openInternal("MANUAL_OPEN");
    }

    /**
     * Opens the cash drawer after a successful cash payment.
     * No permission check — this is always triggered after CASH sales.
     */
    public static boolean openAfterSale(String billNumber) {
        return openInternal("AFTER_SALE:" + billNumber);
    }

    /**
     * Returns true if a cash drawer is configured and reachable.
     */
    public static boolean isAvailable() {
        return printerManager.testPrinter(
            new com.minimartpos.service.SettingsService().get("receipt_printer", ""));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static boolean openInternal(String reason) {
        boolean success = printerManager.openCashDrawer();
        if (success) {
            logger.info("Cash drawer opened. Reason: {} by {}",
                        reason,
                        SessionManager.isLoggedIn()
                            ? SessionManager.getCurrentUser().getUsername() : "system");
            auditService.log("CASH_DRAWER_OPEN", "hardware", 0, null, reason);
        } else {
            logger.warn("Cash drawer open failed. Reason: {}", reason);
        }
        return success;
    }
}

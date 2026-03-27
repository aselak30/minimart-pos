package com.minimartpos.service;

import com.minimartpos.repository.SettingsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Caching wrapper around SettingsRepository.
 * Settings are loaded once and cached; call invalidate() after any write.
 */
public class SettingsService {

    private static final Logger logger = LogManager.getLogger(SettingsService.class);

    private final SettingsRepository repo = new SettingsRepository();
    private Map<String, String> cache = null;

    public Map<String, String> getAll() {
        if (cache == null) cache = repo.loadAll();
        return cache;
    }

    public String get(String key, String defaultValue) {
        return getAll().getOrDefault(key, defaultValue);
    }

    public void set(String key, String value) {
        repo.set(key, value);
        if (cache != null) cache.put(key, value);
        logger.debug("Setting saved: {}={}", key, value);
    }

    public void saveAll(Map<String, String> settings) {
        repo.setAll(settings);
        cache = null; // force reload
        logger.info("All settings saved ({} keys)", settings.size());
        // Notify other machines to reload settings
        try {
            com.minimartpos.network.SyncManager.getInstance().notifySettingsChanged();
        } catch (Exception e) {
            logger.debug("Settings sync notify (non-fatal): {}", e.getMessage());
        }
    }

    public void invalidate() { cache = null; }

    // ── Convenience typed getters ─────────────────────────────────────────────
    public String  company()        { return get("company_name",    "MiniMart"); }
    public String  currency()       { return get("currency_symbol", "Rs."); }
    public boolean taxInclusive()   { return "1".equals(get("tax_inclusive", "0")); }
    public int     lowStockDays()   { return parseInt(get("expiry_warning_days", "30")); }
    public String  receiptFooter()  { return get("receipt_footer", "Thank you!"); }

    private int parseInt(String v) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }
}

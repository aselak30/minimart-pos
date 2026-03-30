package com.minimartpos.service;

import com.minimartpos.model.Category;
import com.minimartpos.model.Product;
import com.minimartpos.model.Supplier;
import com.minimartpos.repository.PriceHistoryRepository;
import com.minimartpos.network.SyncEvent;
import com.minimartpos.repository.CategoryRepository;
import com.minimartpos.repository.ProductRepository;
import com.minimartpos.repository.SupplierRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for product operations.
 * Wraps ProductRepository with caching, validation, and audit hooks.
 */
public class ProductService {

    private static final Logger logger = LogManager.getLogger(ProductService.class);

    private final ProductRepository      productRepo  = new ProductRepository();
    private final CategoryRepository     categoryRepo = new CategoryRepository();
    private final SupplierRepository     supplierRepo = new SupplierRepository();
    private final AuditService           auditService = new AuditService();
    private final PriceHistoryRepository priceHistory = new PriceHistoryRepository();

    // 1-minute cache for POS terminal quick grid
    private List<Product> cache;
    private long          cacheTs;
    private static final long TTL = 60_000;

    // ── Product CRUD ──────────────────────────────────────────────────────────

    public List<Product> getAllActive() {
        long now = System.currentTimeMillis();
        if (cache == null || (now - cacheTs) > TTL) {
            cache   = productRepo.findAllActive();
            cacheTs = now;
        }
        return cache;
    }

    public void invalidateCache() { cache = null; cacheTs = 0; }

    public List<Product> search(String query) {
        if (query == null || query.isBlank()) return getAllActive();
        return productRepo.search(query.trim());
    }

    public Optional<Product> findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        return productRepo.findByBarcode(barcode.trim());
    }

    public Optional<Product> findById(int id) { return productRepo.findById(id); }

    public List<Product> getByCategory(int categoryId) {
        return getAllActive().stream()
                .filter(p -> p.getCategoryId() == categoryId)
                .toList();
    }

    public List<Product> getQuickProducts(int limit) {
        return getAllActive().stream()
                .filter(p -> !p.isOutOfStock())
                .limit(limit)
                .toList();
    }

    public List<String> getActiveCategories() {
        return getAllActive().stream()
                .map(Product::getCategoryName)
                .filter(n -> n != null && !n.isBlank())
                .distinct().sorted().toList();
    }

    public int save(Product product) {
        invalidateCache();
        if (product.getId() == 0) {
            int id = productRepo.insert(product);
            auditService.log("PRODUCT_CREATE", "products", id,
                             null, "barcode=" + product.getBarcode());
            logger.info("Product created: id={} name={}", id, product.getName());
            notify(SyncEvent.Type.PRODUCT_CREATED, id);
            return id;
        } else {
            // Check if price changed before saving — record history if so
            productRepo.findById(product.getId()).ifPresent(existing -> {
                boolean priceChanged = existing.getUnitPrice() != null
                    && existing.getUnitPrice().compareTo(product.getUnitPrice()) != 0;
                boolean costChanged  = existing.getCostPrice() != null
                    && product.getCostPrice() != null
                    && existing.getCostPrice().compareTo(product.getCostPrice()) != 0;
                if (priceChanged || costChanged) {
                    priceHistory.record(
                        product.getId(),
                        existing.getUnitPrice(), product.getUnitPrice(),
                        existing.getCostPrice(), product.getCostPrice(),
                        "Price updated via Product Management");
                }
            });
            productRepo.update(product);
            auditService.log("PRODUCT_UPDATE", "products", product.getId(),
                             null, "name=" + product.getName());
            logger.info("Product updated: id={}", product.getId());
            notify(SyncEvent.Type.PRODUCT_UPDATED, product.getId());
            return product.getId();
        }
    }

    /** Returns full price history for a product. */
    public java.util.List<com.minimartpos.model.PriceHistoryEntry>
            getPriceHistory(int productId) {
        return priceHistory.findByProduct(productId);
    }

    /** Records a manual price change with a specific reason (e.g. from price override). */
    public void recordPriceChange(int productId,
                                  java.math.BigDecimal oldPrice,
                                  java.math.BigDecimal newPrice,
                                  String reason) {
        priceHistory.record(productId, oldPrice, newPrice, null, null, reason);
    }

    private void notify(SyncEvent.Type type, int entityId) {
        try {
            com.minimartpos.network.SyncManager.getInstance()
                .broadcast(new com.minimartpos.network.SyncEvent(type, entityId,
                    com.minimartpos.network.SyncManager.getInstance()
                        .getNetworkMonitor().getLocalMachineCode()));
        } catch (Exception e) {
            logger.debug("Sync notify error (non-fatal): {}", e.getMessage());
        }
    }

    public boolean setActive(int productId, boolean active) {
        Optional<Product> opt = productRepo.findById(productId);
        if (opt.isEmpty()) return false;
        Product p = opt.get();
        p.setActive(active);
        boolean ok = productRepo.update(p);
        if (ok) {
            invalidateCache();
            auditService.log(active ? "PRODUCT_ENABLE" : "PRODUCT_DISABLE",
                             "products", productId, null, null);
        }
        return ok;
    }

    public boolean delete(int id) {
        invalidateCache();
        boolean ok = productRepo.delete(id);
        if (ok) {
            auditService.log("PRODUCT_DELETE", "products", id, null, null);
            logger.info("Product deleted: id={}", id);
            notify(SyncEvent.Type.PRODUCT_UPDATED, id); // Use UPDATED for simple deletion sync
        }
        return ok;
    }

    // ── Reference Data ────────────────────────────────────────────────────────

    public List<Category> getAllCategories() { return categoryRepo.findAllActive(); }
    public List<Supplier> getAllSuppliers()  { return supplierRepo.findAllActive(); }

    public int addCategory(String name) {
        int id = categoryRepo.insert(name);
        auditService.log("CATEGORY_CREATE", "categories", id, null, "name=" + name);
        return id;
    }
}

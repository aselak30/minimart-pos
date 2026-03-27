package com.minimartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents one row in the product_price_history table.
 */
public class PriceHistoryEntry {

    private int           id;
    private int           productId;
    private BigDecimal    oldPrice;
    private BigDecimal    newPrice;
    private BigDecimal    oldCost;
    private BigDecimal    newCost;
    private int           changedBy;
    private String        changedByName;   // denormalised join
    private LocalDateTime changedAt;
    private String        reason;

    // ── Computed helpers ──────────────────────────────────────────────────────

    /** % change in selling price — positive = increase, negative = decrease */
    public BigDecimal priceDeltaPct() {
        if (oldPrice == null || oldPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return newPrice.subtract(oldPrice)
                       .divide(oldPrice, 4, java.math.RoundingMode.HALF_UP)
                       .multiply(BigDecimal.valueOf(100))
                       .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    public boolean isPriceIncrease() {
        return newPrice != null && oldPrice != null
               && newPrice.compareTo(oldPrice) > 0;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int           getId()                          { return id; }
    public void          setId(int v)                     { this.id = v; }
    public int           getProductId()                   { return productId; }
    public void          setProductId(int v)              { this.productId = v; }
    public BigDecimal    getOldPrice()                    { return oldPrice; }
    public void          setOldPrice(BigDecimal v)        { this.oldPrice = v; }
    public BigDecimal    getNewPrice()                    { return newPrice; }
    public void          setNewPrice(BigDecimal v)        { this.newPrice = v; }
    public BigDecimal    getOldCost()                     { return oldCost; }
    public void          setOldCost(BigDecimal v)         { this.oldCost = v; }
    public BigDecimal    getNewCost()                     { return newCost; }
    public void          setNewCost(BigDecimal v)         { this.newCost = v; }
    public int           getChangedBy()                   { return changedBy; }
    public void          setChangedBy(int v)              { this.changedBy = v; }
    public String        getChangedByName()               { return changedByName; }
    public void          setChangedByName(String v)       { this.changedByName = v; }
    public LocalDateTime getChangedAt()                   { return changedAt; }
    public void          setChangedAt(LocalDateTime v)    { this.changedAt = v; }
    public String        getReason()                      { return reason; }
    public void          setReason(String v)              { this.reason = v; }
}

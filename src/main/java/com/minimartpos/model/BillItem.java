package com.minimartpos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A single line item within a Bill.
 */
public class BillItem {

    private int        id;
    private int        billId;
    private int        productId;
    private String     productName;    // denormalized snapshot
    private String     productBarcode; // snapshot
    private int        quantity;
    private BigDecimal unitPrice;      // actual price charged (may differ from catalog)
    private BigDecimal originalPrice;  // catalog price at time of sale
    private BigDecimal costPrice;      // cost at time of sale (for margin calc)
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private BigDecimal discountAmount  = BigDecimal.ZERO;
    private BigDecimal taxRate         = BigDecimal.ZERO;
    private BigDecimal taxAmount       = BigDecimal.ZERO;
    private BigDecimal lineTotal       = BigDecimal.ZERO;
    private BigDecimal lineCostTotal   = BigDecimal.ZERO;
    private boolean    isPriceOverride = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    public BillItem() {}

    public BillItem(Product product, int quantity) {
        this.productId      = product.getId();
        this.productName    = product.getName();
        this.productBarcode = product.getBarcode();
        this.quantity       = quantity;
        this.unitPrice      = product.getUnitPrice();
        this.originalPrice  = product.getUnitPrice();
        this.costPrice      = product.getCostPrice();
        this.taxRate        = product.getTaxRate();
        recalculate();
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    public void recalculate() {
        BigDecimal qty     = BigDecimal.valueOf(quantity);
        BigDecimal gross   = unitPrice.multiply(qty);
        BigDecimal disc    = discountAmount.compareTo(BigDecimal.ZERO) > 0
                             ? discountAmount
                             : gross.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net     = gross.subtract(disc);
        taxAmount          = net.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        lineTotal          = net.add(taxAmount);
        lineCostTotal      = (costPrice != null)
                             ? costPrice.multiply(qty)
                             : BigDecimal.ZERO;
    }

    public BigDecimal getProfit() {
        return lineTotal.subtract(lineCostTotal);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int    getId()               { return id; }
    public void   setId(int id)         { this.id = id; }

    public int    getBillId()                       { return billId; }
    public void   setBillId(int billId)             { this.billId = billId; }

    public int    getProductId()                        { return productId; }
    public void   setProductId(int productId)           { this.productId = productId; }

    public String getProductName()                          { return productName; }
    public void   setProductName(String productName)        { this.productName = productName; }

    public String getProductBarcode()                           { return productBarcode; }
    public void   setProductBarcode(String productBarcode)      { this.productBarcode = productBarcode; }

    public int    getQuantity()                             { return quantity; }
    public void   setQuantity(int quantity)                 { this.quantity = quantity; recalculate(); }

    public BigDecimal getUnitPrice()                            { return unitPrice; }
    public void       setUnitPrice(BigDecimal unitPrice)        { this.unitPrice = unitPrice; recalculate(); }

    public BigDecimal getOriginalPrice()                            { return originalPrice; }
    public void       setOriginalPrice(BigDecimal originalPrice)    { this.originalPrice = originalPrice; }

    public BigDecimal getCostPrice()                                { return costPrice; }
    public void       setCostPrice(BigDecimal costPrice)            { this.costPrice = costPrice; }

    public BigDecimal getDiscountPercent()                              { return discountPercent; }
    public void       setDiscountPercent(BigDecimal discountPercent)    { this.discountPercent = discountPercent; recalculate(); }

    public BigDecimal getDiscountAmount()                               { return discountAmount; }
    public void       setDiscountAmount(BigDecimal discountAmount)      { this.discountAmount = discountAmount; recalculate(); }

    public BigDecimal getTaxRate()                                  { return taxRate; }
    public void       setTaxRate(BigDecimal taxRate)                { this.taxRate = taxRate; recalculate(); }

    public BigDecimal getTaxAmount()                                { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount)                  { this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO; }
    public BigDecimal getLineTotal()                                { return lineTotal; }
    public void       setLineTotal(BigDecimal v)                    { this.lineTotal = v; }
    public BigDecimal getLineCostTotal()                            { return lineCostTotal; }

    public boolean    isPriceOverride()                                     { return isPriceOverride; }
    public void       setPriceOverride(boolean priceOverride)               { this.isPriceOverride = priceOverride; }
}

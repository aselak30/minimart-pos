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
    private BigDecimal quantity;
    private BigDecimal unitPrice;      // actual price charged (may differ from catalog)
    private BigDecimal originalPrice;  // catalog price at time of sale
    private BigDecimal costPrice;      // cost at time of sale (for margin calc)
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private BigDecimal discountAmount  = BigDecimal.ZERO;
    private BigDecimal taxRate         = BigDecimal.ZERO;
    private BigDecimal taxAmount       = BigDecimal.ZERO;
    private BigDecimal lineTotal       = BigDecimal.ZERO;
    private BigDecimal lineCostTotal   = BigDecimal.ZERO;
    private BigDecimal weight          = BigDecimal.ZERO;
    private String     weightUnit;
    private boolean    isWeightBased   = false;
    private boolean    isPriceOverride = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    public BillItem() {}

    public BillItem(Product product, BigDecimal quantity) {
        this.productId      = product.getId();
        this.productName    = product.getName();
        this.productBarcode = product.getBarcode();
        this.quantity       = quantity;
        this.unitPrice      = product.getUnitPrice();
        this.originalPrice  = product.getUnitPrice();
        this.costPrice      = product.getCostPrice();
        this.taxRate        = product.getTaxRate();
        this.isWeightBased  = product.isWeightBased();
        this.weightUnit     = product.getWeightUnit();
        if (this.isWeightBased) {
            this.weight     = quantity;
            this.unitPrice  = product.getPricePerUnit();
        }
        recalculate();
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    public void recalculate() {
        if (unitPrice == null) return; // Prevent NPE if called before unitPrice is set
        BigDecimal baseQty = isWeightBased ? weight : quantity;
        if (baseQty == null) baseQty = BigDecimal.ZERO;
        BigDecimal gross   = unitPrice.multiply(baseQty);
        BigDecimal disc    = (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0)
                             ? discountAmount
                             : gross.multiply(discountPercent != null ? discountPercent : BigDecimal.ZERO)
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net     = gross.subtract(disc);
        taxAmount          = net.multiply(taxRate != null ? taxRate : BigDecimal.ZERO)
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        lineTotal          = net.add(taxAmount);
        lineCostTotal      = (costPrice != null)
                             ? costPrice.multiply(baseQty)
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

    public BigDecimal getQuantity()                         { return isWeightBased ? weight : quantity; }
    public void       setQuantity(BigDecimal quantity)         { 
        if (isWeightBased) this.weight = quantity; 
        else this.quantity = quantity; 
        recalculate(); 
    }

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

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; recalculate(); }

    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }

    public boolean isWeightBased() { return isWeightBased; }
    public void setWeightBased(boolean weightBased) { isWeightBased = weightBased; }
}

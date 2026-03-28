package com.minimartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a product/item in inventory.
 * Cost price is hidden from cashiers without the VIEW_COST_PRICE permission.
 */
public class Product {

    private int        id;
    private String     barcode;
    private String     name;
    private int        categoryId;
    private String     categoryName;   // denormalized for display
    private String     brand;
    private String     sizeWeight;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private BigDecimal taxRate;
    private boolean    discountAllowed;
    private BigDecimal maxDiscountPercent;
    private BigDecimal stockQuantity;
    private BigDecimal reorderLevel;
    private LocalDate  expiryDate;
    private String     batchNumber;
    private int        supplierId;
    private String     supplierName;   // denormalized for display
    private String     location;
    private boolean    active;
    private byte[]     image;
    private String     imagePath;
    private String     description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean       isWeightBased;
    private String        weightUnit;       // e.g., kg, g, lb, oz
    private BigDecimal    pricePerUnit;     // price per weight unit
    private BigDecimal    defaultWeight;    // preset weight value
    private BigDecimal    minWeight;        // minimum allowed entry
    private BigDecimal    maxWeight;        // maximum allowed entry

    // ── Constructors ──────────────────────────────────────────────────────────

    public Product() {
        this.active          = true;
        this.discountAllowed = true;
        this.taxRate         = BigDecimal.ZERO;
        this.stockQuantity   = BigDecimal.ZERO;
        this.reorderLevel    = BigDecimal.valueOf(5);
        this.createdAt       = LocalDateTime.now();
    }

    // ── Computed Helpers ──────────────────────────────────────────────────────

    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity.compareTo(reorderLevel) <= 0;
    }

    public boolean isOutOfStock() {
        return stockQuantity == null || stockQuantity.compareTo(BigDecimal.ZERO) <= 0;
    }

    public boolean isExpiringSoon(int withinDays) {
        if (expiryDate == null) return false;
        return !expiryDate.isBefore(LocalDate.now()) &&
                expiryDate.isBefore(LocalDate.now().plusDays(withinDays));
    }

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public BigDecimal getProfitMargin() {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return unitPrice.subtract(costPrice)
                        .divide(unitPrice, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal getProfitAmount() {
        if (costPrice == null) return BigDecimal.ZERO;
        return unitPrice.subtract(costPrice);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int         getId()               { return id; }
    public void        setId(int id)         { this.id = id; }

    public String      getBarcode()                      { return barcode; }
    public void        setBarcode(String barcode)        { this.barcode = barcode; }

    public String      getName()                         { return name; }
    public void        setName(String name)              { this.name = name; }

    public int         getCategoryId()                        { return categoryId; }
    public void        setCategoryId(int categoryId)          { this.categoryId = categoryId; }

    public String      getCategoryName()                      { return categoryName; }
    public void        setCategoryName(String categoryName)   { this.categoryName = categoryName; }

    public String      getBrand()                        { return brand; }
    public void        setBrand(String brand)            { this.brand = brand; }

    public String      getSizeWeight()                        { return sizeWeight; }
    public void        setSizeWeight(String sizeWeight)       { this.sizeWeight = sizeWeight; }

    public BigDecimal  getUnitPrice()                         { return unitPrice; }
    public void        setUnitPrice(BigDecimal unitPrice)     { this.unitPrice = unitPrice; }

    public BigDecimal  getCostPrice()                         { return costPrice; }
    public void        setCostPrice(BigDecimal costPrice)     { this.costPrice = costPrice; }

    public BigDecimal  getTaxRate()                           { return taxRate; }
    public void        setTaxRate(BigDecimal taxRate)         { this.taxRate = taxRate; }

    public boolean     isDiscountAllowed()                          { return discountAllowed; }
    public void        setDiscountAllowed(boolean discountAllowed)  { this.discountAllowed = discountAllowed; }

    public BigDecimal  getMaxDiscountPercent()                          { return maxDiscountPercent; }
    public void        setMaxDiscountPercent(BigDecimal maxDiscountPercent) { this.maxDiscountPercent = maxDiscountPercent; }

    public BigDecimal  getStockQuantity()                           { return stockQuantity; }
    public void        setStockQuantity(BigDecimal stockQuantity)          { this.stockQuantity = stockQuantity; }

    public BigDecimal  getReorderLevel()                            { return reorderLevel; }
    public void        setReorderLevel(BigDecimal reorderLevel)            { this.reorderLevel = reorderLevel; }

    public LocalDate   getExpiryDate()                              { return expiryDate; }
    public void        setExpiryDate(LocalDate expiryDate)          { this.expiryDate = expiryDate; }

    public String      getBatchNumber()                             { return batchNumber; }
    public void        setBatchNumber(String batchNumber)           { this.batchNumber = batchNumber; }

    public int         getSupplierId()                              { return supplierId; }
    public void        setSupplierId(int supplierId)                { this.supplierId = supplierId; }

    public String      getSupplierName()                            { return supplierName; }
    public void        setSupplierName(String supplierName)         { this.supplierName = supplierName; }

    public String      getLocation()                                { return location; }
    public void        setLocation(String location)                 { this.location = location; }

    public boolean     isActive()                                   { return active; }
    public void        setActive(boolean active)                    { this.active = active; }

    public byte[]      getImage()                                   { return image; }
    public void        setImage(byte[] image)                       { this.image = image; }

    public String      getImagePath()                               { return imagePath; }
    public void        setImagePath(String imagePath)               { this.imagePath = imagePath; }

    public String      getDescription()                             { return description; }
    public void        setDescription(String description)           { this.description = description; }

    public LocalDateTime getCreatedAt()                             { return createdAt; }
    public void          setCreatedAt(LocalDateTime createdAt)      { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt()                             { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime updatedAt)      { this.updatedAt = updatedAt; }

    public boolean isWeightBased() { return isWeightBased; }
    public void setWeightBased(boolean weightBased) { isWeightBased = weightBased; }

    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public BigDecimal getDefaultWeight() { return defaultWeight; }
    public void setDefaultWeight(BigDecimal defaultWeight) { this.defaultWeight = defaultWeight; }

    public BigDecimal getMinWeight() { return minWeight; }
    public void setMinWeight(BigDecimal minWeight) { this.minWeight = minWeight; }

    public BigDecimal getMaxWeight() { return maxWeight; }
    public void setMaxWeight(BigDecimal maxWeight) { this.maxWeight = maxWeight; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", barcode='" + barcode + "', name='" + name + "', price=" + unitPrice + "}";
    }
}

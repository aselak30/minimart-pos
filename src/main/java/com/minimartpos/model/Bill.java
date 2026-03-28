package com.minimartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sales transaction (bill/invoice).
 */
public class Bill {

    public enum Status    { DRAFT, FINALIZED, VOIDED, REFUNDED }
    public enum PayType   { CASH, CARD, MOBILE_MONEY, CREDIT, SPLIT }

    private int           id;
    private String        billNumber;     // e.g. BILL-20240316-0001
    private int           cashierId;
    private String        cashierName;    // denormalized
    private int           customerId;
    private String        customerName;   // denormalized
    private int           machineId;
    private List<BillItem>items           = new ArrayList<>();

    private BigDecimal    subtotal        = BigDecimal.ZERO;
    private BigDecimal    discountPercent = BigDecimal.ZERO;
    private BigDecimal    discountAmount  = BigDecimal.ZERO;
    private BigDecimal    taxAmount       = BigDecimal.ZERO;
    private BigDecimal    totalAmount     = BigDecimal.ZERO;
    private BigDecimal    paidAmount      = BigDecimal.ZERO;
    private BigDecimal    changeAmount    = BigDecimal.ZERO;
    private BigDecimal    costTotal       = BigDecimal.ZERO;   // hidden from cashiers
    private BigDecimal    profitTotal     = BigDecimal.ZERO;   // hidden from cashiers

    private PayType       paymentType;
    private Status        status          = Status.DRAFT;
    private String        voidReason;
    private String        notes;           // audit trail / correction notes

    private LocalDateTime createdAt;
    private LocalDateTime finalizedAt;
    private boolean       isEditable = true;
    private int           deletedBy;
    private String        deletedReason;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Bill() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    public void recalculate() {
        subtotal    = BigDecimal.ZERO;
        taxAmount   = BigDecimal.ZERO;
        costTotal   = BigDecimal.ZERO;

        for (BillItem item : items) {
            item.recalculate();
            subtotal  = subtotal.add(item.getLineTotal());
            taxAmount = taxAmount.add(item.getTaxAmount());
            costTotal = costTotal.add(item.getLineCostTotal());
        }

        totalAmount  = subtotal.add(taxAmount).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) totalAmount = BigDecimal.ZERO;

        profitTotal  = totalAmount.subtract(costTotal);
        changeAmount = paidAmount.subtract(totalAmount);
        if (changeAmount.compareTo(BigDecimal.ZERO) < 0) changeAmount = BigDecimal.ZERO;
    }

    public int getItemCount() {
        if (items == null) return 0;
        return items.stream()
                .map(item -> item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .intValue();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int    getId()               { return id; }
    public void   setId(int id)         { this.id = id; }

    public String getBillNumber()                      { return billNumber; }
    public void   setBillNumber(String billNumber)     { this.billNumber = billNumber; }

    public int    getCashierId()                        { return cashierId; }
    public void   setCashierId(int cashierId)           { this.cashierId = cashierId; }

    public String getCashierName()                         { return cashierName; }
    public void   setCashierName(String cashierName)       { this.cashierName = cashierName; }

    public int    getCustomerId()                           { return customerId; }
    public void   setCustomerId(int customerId)             { this.customerId = customerId; }

    public String getCustomerName()                        { return customerName; }
    public void   setCustomerName(String customerName)     { this.customerName = customerName; }

    public int    getMachineId()                            { return machineId; }
    public void   setMachineId(int machineId)               { this.machineId = machineId; }

    public List<BillItem> getItems()                        { return items; }
    public void           setItems(List<BillItem> items)    { this.items = items; }

    public BigDecimal getSubtotal()                             { return subtotal; }
    public void       setSubtotal(BigDecimal subtotal)          { this.subtotal = subtotal; }

    public BigDecimal getDiscountPercent()                                    { return discountPercent; }
    public void       setDiscountPercent(BigDecimal discountPercent)          { this.discountPercent = discountPercent; }

    public BigDecimal getDiscountAmount()                               { return discountAmount; }
    public void       setDiscountAmount(BigDecimal discountAmount)      { this.discountAmount = discountAmount; }

    public BigDecimal getTaxAmount()                                    { return taxAmount; }
    public void       setTaxAmount(BigDecimal taxAmount)                { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount()                                  { return totalAmount; }
    public void       setTotalAmount(BigDecimal totalAmount)            { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount()                                   { return paidAmount; }
    public void       setPaidAmount(BigDecimal paidAmount)              { this.paidAmount = paidAmount; }

    public BigDecimal getChangeAmount()                                 { return changeAmount; }
    public void       setChangeAmount(BigDecimal changeAmount)          { this.changeAmount = changeAmount; }

    public BigDecimal getCostTotal()                                    { return costTotal; }
    public BigDecimal getProfitTotal()                                  { return profitTotal; }

    public PayType    getPaymentType()                                  { return paymentType; }
    public void       setPaymentType(PayType paymentType)               { this.paymentType = paymentType; }

    public Status     getStatus()                                       { return status; }
    public void       setStatus(Status status)                          { this.status = status; }

    public String     getVoidReason()                                   { return voidReason; }
    public void       setVoidReason(String voidReason)                  { this.voidReason = voidReason; }

    public String     getNotes()                                        { return notes; }
    public void       setNotes(String notes)                            { this.notes = notes; }

    public LocalDateTime getCreatedAt()                                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime createdAt)          { this.createdAt = createdAt; }

    public LocalDateTime getFinalizedAt()                               { return finalizedAt; }
    public void          setFinalizedAt(LocalDateTime finalizedAt)      { this.finalizedAt = finalizedAt; }

    public boolean isEditable() { return isEditable; }
    public void setEditable(boolean editable) { isEditable = editable; }

    public int getDeletedBy() { return deletedBy; }
    public void setDeletedBy(int deletedBy) { this.deletedBy = deletedBy; }

    public String getDeletedReason() { return deletedReason; }
    public void setDeletedReason(String deletedReason) { this.deletedReason = deletedReason; }
}

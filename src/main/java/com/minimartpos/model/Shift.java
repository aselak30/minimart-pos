package com.minimartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a cashier's work shift session.
 */
public class Shift {

    public enum Status { OPEN, CLOSED }

    private int           id;
    private int           cashierId;
    private String        cashierName;      // denormalized
    private int           machineId;
    private String        machineName;      // denormalized
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal    openingCash  = BigDecimal.ZERO;
    private BigDecimal    closingCash  = BigDecimal.ZERO;
    private BigDecimal    totalSales   = BigDecimal.ZERO;
    private int           totalBills   = 0;
    private Status        status       = Status.OPEN;
    private String        notes;

    public int           getId()                              { return id; }
    public void          setId(int id)                        { this.id = id; }
    public int           getCashierId()                       { return cashierId; }
    public void          setCashierId(int v)                  { this.cashierId = v; }
    public String        getCashierName()                     { return cashierName; }
    public void          setCashierName(String v)             { this.cashierName = v; }
    public int           getMachineId()                       { return machineId; }
    public void          setMachineId(int v)                  { this.machineId = v; }
    public String        getMachineName()                     { return machineName; }
    public void          setMachineName(String v)             { this.machineName = v; }
    public LocalDateTime getStartTime()                       { return startTime; }
    public void          setStartTime(LocalDateTime v)        { this.startTime = v; }
    public LocalDateTime getEndTime()                         { return endTime; }
    public void          setEndTime(LocalDateTime v)          { this.endTime = v; }
    public BigDecimal    getOpeningCash()                     { return openingCash; }
    public void          setOpeningCash(BigDecimal v)         { this.openingCash = v; }
    public BigDecimal    getClosingCash()                     { return closingCash; }
    public void          setClosingCash(BigDecimal v)         { this.closingCash = v; }
    public BigDecimal    getTotalSales()                      { return totalSales; }
    public void          setTotalSales(BigDecimal v)          { this.totalSales = v; }
    public int           getTotalBills()                      { return totalBills; }
    public void          setTotalBills(int v)                 { this.totalBills = v; }
    public Status        getStatus()                          { return status; }
    public void          setStatus(Status v)                  { this.status = v; }
    public String        getNotes()                           { return notes; }
    public void          setNotes(String v)                   { this.notes = v; }
}

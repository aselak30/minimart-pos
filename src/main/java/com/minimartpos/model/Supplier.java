package com.minimartpos.model;

import java.time.LocalDateTime;

public class Supplier {
    private int           id;
    private String        name;
    private String        contactName;
    private String        phone;
    private String        email;
    private String        address;
    private boolean       active = true;
    private LocalDateTime createdAt;

    public Supplier() {}
    public Supplier(int id, String name) { this.id = id; this.name = name; }

    public int    getId()              { return id; }
    public void   setId(int id)        { this.id = id; }
    public String getName()            { return name; }
    public void   setName(String n)    { this.name = n; }
    public String getContactName()     { return contactName; }
    public void   setContactName(String n){ this.contactName = n; }
    public String getPhone()           { return phone; }
    public void   setPhone(String p)   { this.phone = p; }
    public String getEmail()           { return email; }
    public void   setEmail(String e)   { this.email = e; }
    public String getAddress()         { return address; }
    public void   setAddress(String a) { this.address = a; }
    public boolean isActive()          { return active; }
    public void   setActive(boolean a) { this.active = a; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public void   setCreatedAt(LocalDateTime t){ this.createdAt = t; }

    @Override public String toString() { return name; }
}

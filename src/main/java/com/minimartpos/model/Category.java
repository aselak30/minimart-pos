package com.minimartpos.model;

import java.time.LocalDateTime;

public class Category {
    private int           id;
    private String        name;
    private int           parentId;
    private boolean       active = true;
    private LocalDateTime createdAt;

    public Category() {}
    public Category(int id, String name) { this.id = id; this.name = name; }

    public int           getId()          { return id; }
    public void          setId(int id)    { this.id = id; }
    public String        getName()        { return name; }
    public void          setName(String n){ this.name = n; }
    public int           getParentId()    { return parentId; }
    public void          setParentId(int p){ this.parentId = p; }
    public boolean       isActive()       { return active; }
    public void          setActive(boolean a){ this.active = a; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public void          setCreatedAt(LocalDateTime t){ this.createdAt = t; }

    @Override public String toString() { return name; } // for ComboBox display
}

package com.minimartpos.model;

import java.time.LocalDateTime;

/**
 * A key/value application setting row.
 */
public class Setting {
    private String        key;
    private String        value;
    private String        description;
    private LocalDateTime updatedAt;

    public Setting() {}
    public Setting(String key, String value) { this.key = key; this.value = value; }

    public String        getKey()              { return key; }
    public void          setKey(String k)      { this.key = k; }
    public String        getValue()            { return value; }
    public void          setValue(String v)    { this.value = v; }
    public String        getDescription()      { return description; }
    public void          setDescription(String d){ this.description = d; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime t){ this.updatedAt = t; }
}

package com.minimartpos.util;

import com.minimartpos.security.PasswordHasher;

public class PasswordGenerator {
    public static void main(String[] args) {
        System.out.println("Admin@123: " + PasswordHasher.hash("Admin@123"));
        System.out.println("Superm!n@POS2024#: " + PasswordHasher.hash("Superm!n@POS2024#"));
    }
}

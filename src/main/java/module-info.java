module com.minimartpos {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.swing;

    // ControlsFX
    requires org.controlsfx.controls;

    // Ikonli
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.ikonli.material2;

    // Database
    requires java.sql;
    requires com.zaxxer.hikari;

    // Security
    requires bcrypt;

    // Logging
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    // Excel
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;

    // Barcode
    requires com.google.zxing;
    requires com.google.zxing.javase;

    // JSON
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // Commons
    requires org.apache.commons.lang3;

    // Networking / Print / Desktop / Preferences
    requires java.net.http;
    requires java.desktop;
    requires java.prefs;

    // iText PDF
    requires kernel;
    requires layout;
    requires io;

    // Opens needed for JavaFX FXML reflection
    opens com.minimartpos.app to javafx.graphics, javafx.fxml;
    opens com.minimartpos.controller.admin to javafx.fxml;
    opens com.minimartpos.controller.cashier to javafx.fxml;
    opens com.minimartpos.controller.shared to javafx.fxml;
    opens com.minimartpos.model to javafx.base, com.fasterxml.jackson.databind;
    opens com.minimartpos.model.enums to javafx.base, javafx.fxml;
    opens com.minimartpos.network to com.fasterxml.jackson.databind;
    opens com.minimartpos.config to com.fasterxml.jackson.databind;

    // Exports
    exports com.minimartpos.app;
    exports com.minimartpos.model;
    exports com.minimartpos.service;
    exports com.minimartpos.repository;
    exports com.minimartpos.util;
    exports com.minimartpos.config;
    exports com.minimartpos.security;
    exports com.minimartpos.network;
}

package com.minimartpos.app;

/**
 * Launcher class - required workaround when running JavaFX from a fat (shaded) JAR.
 * The JVM needs the main class to NOT extend Application directly for module detection.
 * This class simply delegates to MainApp.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}

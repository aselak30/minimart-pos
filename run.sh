#!/bin/bash
# ============================================================================
# MiniMart POS Ultimate - Launcher (Linux / macOS)
# ============================================================================

JAR="target/minimart-pos-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Application not built. Please run: ./setup.sh"
    exit 1
fi

java -jar "$JAR"

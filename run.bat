@echo off
:: ============================================================================
:: MiniMart POS Ultimate - Launcher (Windows)
:: Run after mvn clean package -DskipTests has been completed.
:: ============================================================================
title MiniMart POS Ultimate

:: Check if the JAR was built
if not exist "target\minimart-pos-1.0.0-SNAPSHOT.jar" (
    echo.
    echo  Application not built yet.
    echo  Please run:  mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

:: Launch the application
:: --add-reads / --add-opens allow non-modular libraries (MySQL, BCrypt, iText, etc.)
java ^
  --add-reads com.minimartpos=ALL-UNNAMED ^
  --add-opens com.minimartpos/com.minimartpos.controller.admin=javafx.fxml ^
  --add-opens com.minimartpos/com.minimartpos.controller.cashier=javafx.fxml ^
  --add-opens com.minimartpos/com.minimartpos.controller.shared=javafx.fxml ^
  --add-opens com.minimartpos/com.minimartpos.model=ALL-UNNAMED ^
  -jar target\minimart-pos-1.0.0-SNAPSHOT.jar

pause

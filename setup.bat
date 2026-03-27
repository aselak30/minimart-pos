@echo off
:: ============================================================================
:: MiniMart POS Ultimate - Setup Launcher
:: Double-click this file to start the installer.
:: Works on Windows 10 and Windows 11.
:: ============================================================================

title MiniMart POS Ultimate - Setup

:: Check PowerShell is available (it always is on Win10/11)
where powershell >nul 2>&1
if errorlevel 1 (
    echo PowerShell is not available on this machine.
    echo Please install Windows PowerShell 5 or later.
    pause
    exit /b 1
)

:: Run the PowerShell GUI installer
:: -ExecutionPolicy Bypass  = no need to change system policy
:: -NoProfile               = faster startup
:: -File                    = run the script file
powershell -ExecutionPolicy Bypass -NoProfile -NonInteractive ^
    -File "%~dp0setup.ps1"

:: If PowerShell exited with an error, show a fallback message
if errorlevel 1 (
    echo.
    echo  Setup encountered an error.
    echo  Please check SETUP_GUIDE.docx for manual installation instructions.
    echo.
    pause
)

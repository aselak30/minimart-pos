# ==============================================================================
# MiniMart POS Ultimate - Windows GUI Installer
# Requires: Windows 10/11, PowerShell 5+
# Run via: setup.bat  (do NOT run directly — setup.bat sets the right permissions)
# ==============================================================================

# ── Load Windows Forms ─────────────────────────────────────────────────────────
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$SCRIPT_DIR  = Split-Path -Parent $MyInvocation.MyCommand.Path
$APP_NAME    = "MiniMart POS Ultimate"
$APP_VERSION = "1.0.0"
$JAR_NAME    = "minimart-pos-1.0.0-SNAPSHOT.jar"
$INSTALL_DIR = "$env:LOCALAPPDATA\MiniMartPOS"

# ── Colour palette ──────────────────────────────────────────────────────────────
$BLUE   = [System.Drawing.Color]::FromArgb(21,  118, 210)
$DARK   = [System.Drawing.Color]::FromArgb(26,  26,  46)
$WHITE  = [System.Drawing.Color]::White
$GREY   = [System.Drawing.Color]::FromArgb(245, 247, 250)
$GREEN  = [System.Drawing.Color]::FromArgb(46,  125, 50)
$RED    = [System.Drawing.Color]::FromArgb(211, 47,  47)
$ORANGE = [System.Drawing.Color]::FromArgb(230, 81,  0)

# ==============================================================================
# MAIN FORM
# ==============================================================================
$form = New-Object System.Windows.Forms.Form
$form.Text            = "$APP_NAME - Setup v$APP_VERSION"
$form.Size            = New-Object System.Drawing.Size(620, 560)
$form.StartPosition   = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox     = $false
$form.BackColor       = $WHITE
$form.Font            = New-Object System.Drawing.Font("Segoe UI", 9)

# ── Header banner ───────────────────────────────────────────────────────────────
$pnlHeader = New-Object System.Windows.Forms.Panel
$pnlHeader.Dock      = "Top"
$pnlHeader.Height    = 80
$pnlHeader.BackColor = $BLUE
$form.Controls.Add($pnlHeader)

$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text      = "🛒  $APP_NAME"
$lblTitle.ForeColor = $WHITE
$lblTitle.Font      = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
$lblTitle.AutoSize  = $true
$lblTitle.Location  = New-Object System.Drawing.Point(20, 12)
$pnlHeader.Controls.Add($lblTitle)

$lblSubtitle = New-Object System.Windows.Forms.Label
$lblSubtitle.Text      = "Retail Point of Sale System  •  v$APP_VERSION  •  Java 17 + MySQL 8"
$lblSubtitle.ForeColor = [System.Drawing.Color]::FromArgb(200, 230, 255)
$lblSubtitle.Font      = New-Object System.Drawing.Font("Segoe UI", 9)
$lblSubtitle.AutoSize  = $true
$lblSubtitle.Location  = New-Object System.Drawing.Point(22, 52)
$pnlHeader.Controls.Add($lblSubtitle)

# ── Steps panel ────────────────────────────────────────────────────────────────
$pnlSteps = New-Object System.Windows.Forms.Panel
$pnlSteps.Location  = New-Object System.Drawing.Point(0, 80)
$pnlSteps.Size      = New-Object System.Drawing.Size(160, 420)
$pnlSteps.BackColor = [System.Drawing.Color]::FromArgb(235, 243, 254)
$form.Controls.Add($pnlSteps)

$steps = @("Welcome","Requirements","Database","Install","Finish")
$stepLabels = @()
for ($i = 0; $i -lt $steps.Count; $i++) {
    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Text      = "  $($i+1).  $($steps[$i])"
    $lbl.Location  = New-Object System.Drawing.Point(0, ($i * 48 + 16))
    $lbl.Size      = New-Object System.Drawing.Size(160, 38)
    $lbl.Font      = New-Object System.Drawing.Font("Segoe UI", 9)
    $lbl.ForeColor = [System.Drawing.Color]::FromArgb(100,140,180)
    $pnlSteps.Controls.Add($lbl)
    $stepLabels += $lbl
}

function Set-ActiveStep($idx) {
    for ($i = 0; $i -lt $stepLabels.Count; $i++) {
        if ($i -eq $idx) {
            $stepLabels[$i].Font      = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
            $stepLabels[$i].ForeColor = $BLUE
            $stepLabels[$i].BackColor = $WHITE
        } else {
            $stepLabels[$i].Font      = New-Object System.Drawing.Font("Segoe UI", 9)
            $stepLabels[$i].ForeColor = [System.Drawing.Color]::FromArgb(100,140,180)
            $stepLabels[$i].BackColor = [System.Drawing.Color]::Transparent
        }
    }
}

# ── Content area ───────────────────────────────────────────────────────────────
$pnlContent = New-Object System.Windows.Forms.Panel
$pnlContent.Location  = New-Object System.Drawing.Point(160, 80)
$pnlContent.Size      = New-Object System.Drawing.Size(450, 380)
$pnlContent.BackColor = $WHITE
$form.Controls.Add($pnlContent)

# ── Status bar ────────────────────────────────────────────────────────────────
$statusBar = New-Object System.Windows.Forms.StatusStrip
$statusLabel = New-Object System.Windows.Forms.ToolStripStatusLabel
$statusLabel.Text = "Ready"
$statusBar.Items.Add($statusLabel) | Out-Null
$form.Controls.Add($statusBar)

# ── Progress bar ──────────────────────────────────────────────────────────────
$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Location = New-Object System.Drawing.Point(160, 460)
$progress.Size     = New-Object System.Drawing.Size(450, 22)
$progress.Minimum  = 0
$progress.Maximum  = 100
$progress.Value    = 0
$form.Controls.Add($progress)

# ── Navigation buttons ────────────────────────────────────────────────────────
$btnBack = New-Object System.Windows.Forms.Button
$btnBack.Text     = "< Back"
$btnBack.Size     = New-Object System.Drawing.Size(90, 30)
$btnBack.Location = New-Object System.Drawing.Point(270, 490)
$btnBack.Enabled  = $false
$form.Controls.Add($btnBack)

$btnNext = New-Object System.Windows.Forms.Button
$btnNext.Text      = "Next >"
$btnNext.Size      = New-Object System.Drawing.Size(90, 30)
$btnNext.Location  = New-Object System.Drawing.Point(370, 490)
$btnNext.BackColor = $BLUE
$btnNext.ForeColor = $WHITE
$btnNext.FlatStyle = "Flat"
$form.Controls.Add($btnNext)

$btnCancel = New-Object System.Windows.Forms.Button
$btnCancel.Text     = "Cancel"
$btnCancel.Size     = New-Object System.Drawing.Size(90, 30)
$btnCancel.Location = New-Object System.Drawing.Point(470, 490)
$form.Controls.Add($btnCancel)

# ==============================================================================
# HELPER FUNCTIONS
# ==============================================================================

function Add-SectionTitle($parent, $text, $y) {
    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Text      = $text
    $lbl.Font      = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
    $lbl.ForeColor = $DARK
    $lbl.AutoSize  = $true
    $lbl.Location  = New-Object System.Drawing.Point(20, $y)
    $parent.Controls.Add($lbl)
    return $lbl
}

function Add-Info($parent, $text, $y, $color) {
    if (-not $color) { $color = [System.Drawing.Color]::FromArgb(60,60,80) }
    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Text      = $text
    $lbl.ForeColor = $color
    $lbl.Font      = New-Object System.Drawing.Font("Segoe UI", 9)
    $lbl.AutoSize  = $false
    $lbl.Size      = New-Object System.Drawing.Size(420, 20)
    $lbl.Location  = New-Object System.Drawing.Point(20, $y)
    $parent.Controls.Add($lbl)
    return $lbl
}

function Add-InputRow($parent, $label, $default, $y, $isPassword) {
    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Text     = $label
    $lbl.Font     = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $lbl.AutoSize = $true
    $lbl.Location = New-Object System.Drawing.Point(20, $y)
    $parent.Controls.Add($lbl)

    $txt = New-Object System.Windows.Forms.TextBox
    $txt.Text     = $default
    $txt.Size     = New-Object System.Drawing.Size(260, 24)
    $txt.Location = New-Object System.Drawing.Point(150, ($y - 2))
    if ($isPassword) { $txt.PasswordChar = "*" }
    $parent.Controls.Add($txt)
    return $txt
}

function Set-Status($msg) {
    $statusLabel.Text = $msg
    $statusBar.Refresh()
    [System.Windows.Forms.Application]::DoEvents()
}

function Set-Progress($val) {
    $progress.Value = [Math]::Min($val, 100)
    [System.Windows.Forms.Application]::DoEvents()
}

function Show-CheckRow($parent, $label, $ok, $detail, $y) {
    $icon = if ($ok) { "✔" } else { "✖" }
    $color = if ($ok) { $GREEN } else { $RED }
    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Text      = "$icon  $label"
    $lbl.ForeColor = $color
    $lbl.Font      = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
    $lbl.AutoSize  = $true
    $lbl.Location  = New-Object System.Drawing.Point(20, $y)
    $parent.Controls.Add($lbl)

    if ($detail) {
        $det = New-Object System.Windows.Forms.Label
        $det.Text      = "     $detail"
        $det.ForeColor = [System.Drawing.Color]::FromArgb(80,80,100)
        $det.Font      = New-Object System.Drawing.Font("Segoe UI", 8)
        $det.AutoSize  = $true
        $det.Location  = New-Object System.Drawing.Point(20, ($y + 18))
        $parent.Controls.Add($det)
    }
    return $ok
}

# ==============================================================================
# PAGE DEFINITIONS
# ==============================================================================

$currentPage = 0
$checkResults = @{}
$dbConfig     = @{}
$installOk    = $false

# ── PAGE 0: Welcome ────────────────────────────────────────────────────────────
function Show-WelcomePage {
    $pnlContent.Controls.Clear()
    Set-ActiveStep 0
    Set-Progress 0

    Add-SectionTitle $pnlContent "Welcome to MiniMart POS Ultimate Setup" 20
    Add-Info $pnlContent "This wizard will install MiniMart POS Ultimate on your computer." 50
    Add-Info $pnlContent "It will:" 75

    $items = @(
        "✔  Check Java 17 (required)",
        "✔  Check Maven (required for building)",
        "✔  Check MySQL 8 (required for database)",
        "✔  Create the database and user account",
        "✔  Build the application",
        "✔  Create a Desktop shortcut",
        "✔  Add to Start Menu"
    )
    $y = 100
    foreach ($item in $items) {
        Add-Info $pnlContent $item $y $BLUE
        $y += 24
    }

    $box = New-Object System.Windows.Forms.Panel
    $box.Location  = New-Object System.Drawing.Point(20, 285)
    $box.Size      = New-Object System.Drawing.Size(410, 58)
    $box.BackColor = [System.Drawing.Color]::FromArgb(255,248,225)
    $pnlContent.Controls.Add($box)

    $warn = New-Object System.Windows.Forms.Label
    $warn.Text      = "⚠  Requires: Java 17+, Maven 3.8+, MySQL 8+ to be installed first.`n   See SETUP_GUIDE.docx for download links."
    $warn.ForeColor = $ORANGE
    $warn.Font      = New-Object System.Drawing.Font("Segoe UI", 8)
    $warn.AutoSize  = $false
    $warn.Size      = New-Object System.Drawing.Size(400, 44)
    $warn.Location  = New-Object System.Drawing.Point(8, 7)
    $box.Controls.Add($warn)

    Add-Info $pnlContent "Default login after setup:   admin / Admin@123" 355 $DARK

    $btnBack.Enabled = $false
    $btnNext.Text    = "Next >"
    $btnNext.Enabled = $true
}

# ── PAGE 1: Requirements check ────────────────────────────────────────────────
function Show-RequirementsPage {
    $pnlContent.Controls.Clear()
    Set-ActiveStep 1
    Set-Progress 10

    Add-SectionTitle $pnlContent "Checking Requirements..." 20
    Set-Status "Checking Java..."
    Set-Progress 15

    # Java check
    $javaOk = $false; $javaVer = "Not found"
    try {
        $jOut = & java -version 2>&1
        $javaVer = ($jOut | Select-Object -First 1) -replace ".*version `"([^`"]+)`".*",'$1'
        $major = [int]($javaVer -split "[.\-]")[0]
        $javaOk = ($major -ge 17)
    } catch {}
    $checkResults["java"] = $javaOk

    Set-Status "Checking Maven..."
    Set-Progress 30

    # Maven check
    $mvnOk = $false; $mvnVer = "Not found"
    try {
        $mOut = & mvn -version 2>&1
        $mvnVer = ($mOut | Select-Object -First 1) -replace "Apache Maven ([0-9.]+).*",'$1'
        $mvnOk = $true
    } catch {}
    $checkResults["maven"] = $mvnOk

    Set-Status "Checking MySQL..."
    Set-Progress 45

    # MySQL check
    $mysqlOk = $false; $mysqlVer = "Not found"
    try {
        $myOut = & mysql --version 2>&1
        $mysqlVer = ($myOut | Select-Object -First 1)
        $mysqlOk = $true
    } catch {}
    $checkResults["mysql"] = $mysqlOk

    Set-Progress 50
    Set-Status "Ready"

    # Show results
    Show-CheckRow $pnlContent "Java 17+" $javaOk "Found: $javaVer" 60
    Show-CheckRow $pnlContent "Maven 3.8+" $mvnOk "Found: $mvnVer" 110
    Show-CheckRow $pnlContent "MySQL 8+" $mysqlOk "Found: $mysqlVer" 160

    $allOk = $javaOk -and $mvnOk

    if (-not $javaOk) {
        Add-Info $pnlContent "Download Java 17: https://adoptium.net/temurin/releases/?version=17" 215 $RED
    }
    if (-not $mvnOk) {
        Add-Info $pnlContent "Download Maven: https://maven.apache.org/download.cgi" 235 $RED
    }
    if (-not $mysqlOk) {
        Add-Info $pnlContent "⚠ MySQL not found in PATH. You can still continue but will need to" 215 $ORANGE
        Add-Info $pnlContent "   set up the database manually. See SETUP_GUIDE.docx" 233 $ORANGE
    }

    if ($allOk) {
        Add-Info $pnlContent "✔  Core requirements satisfied. Click Next to continue." 280 $GREEN
    } else {
        Add-Info $pnlContent "✖  Please install missing requirements and re-run setup." 280 $RED
    }

    $btnBack.Enabled = $true
    $btnNext.Enabled = $javaOk -and $mvnOk
}

# ── PAGE 2: Database configuration ───────────────────────────────────────────
function Show-DatabasePage {
    $pnlContent.Controls.Clear()
    Set-ActiveStep 2
    Set-Progress 50

    Add-SectionTitle $pnlContent "Database Configuration" 20
    Add-Info $pnlContent "Enter your MySQL connection details below." 48

    $script:txtDbHost    = Add-InputRow $pnlContent "MySQL Host:"           "localhost"   80  $false
    $script:txtDbPort    = Add-InputRow $pnlContent "MySQL Port:"           "3306"        114 $false
    $script:txtRootPass  = Add-InputRow $pnlContent "Root Password:"        ""            148 $true
    $script:txtPosPass   = Add-InputRow $pnlContent "POS User Password:"    "posuser123"  182 $true

    Add-Info $pnlContent "(A new user 'pos_user' will be created with this password)" 208 [System.Drawing.Color]::Gray

    $script:chkSkipDb = New-Object System.Windows.Forms.CheckBox
    $script:chkSkipDb.Text     = "Skip database setup (I will configure manually)"
    $script:chkSkipDb.Location = New-Object System.Drawing.Point(20, 240)
    $script:chkSkipDb.AutoSize = $true
    $script:chkSkipDb.Font     = New-Object System.Drawing.Font("Segoe UI", 9)
    $pnlContent.Controls.Add($script:chkSkipDb)

    $script:chkSkipDb.Add_CheckedChanged({
        $skip = $script:chkSkipDb.Checked
        $script:txtRootPass.Enabled = -not $skip
    })

    $box = New-Object System.Windows.Forms.Panel
    $box.Location  = New-Object System.Drawing.Point(20, 275)
    $box.Size      = New-Object System.Drawing.Size(410, 68)
    $box.BackColor = [System.Drawing.Color]::FromArgb(232,245,233)
    $pnlContent.Controls.Add($box)
    $tip = New-Object System.Windows.Forms.Label
    $tip.Text      = "ℹ  'pos_user' will only have access to minimart_pos database.`n   It cannot see or modify any other MySQL databases.`n   Safe for shared MySQL servers."
    $tip.Font      = New-Object System.Drawing.Font("Segoe UI", 8)
    $tip.ForeColor = $GREEN
    $tip.AutoSize  = $false
    $tip.Size      = New-Object System.Drawing.Size(400, 56)
    $tip.Location  = New-Object System.Drawing.Point(8, 6)
    $box.Controls.Add($tip)

    $btnBack.Enabled = $true
    $btnNext.Enabled = $true
    $btnNext.Text    = "Install >"
}

# ── PAGE 3: Installing ───────────────────────────────────────────────────────
function Show-InstallPage {
    $pnlContent.Controls.Clear()
    Set-ActiveStep 3
    Set-Progress 55

    Add-SectionTitle $pnlContent "Installing MiniMart POS..." 20

    $script:logBox = New-Object System.Windows.Forms.RichTextBox
    $script:logBox.Location  = New-Object System.Drawing.Point(20, 50)
    $script:logBox.Size      = New-Object System.Drawing.Size(410, 280)
    $script:logBox.ReadOnly  = $true
    $script:logBox.BackColor = [System.Drawing.Color]::FromArgb(20,20,35)
    $script:logBox.ForeColor = [System.Drawing.Color]::FromArgb(200,255,200)
    $script:logBox.Font      = New-Object System.Drawing.Font("Consolas", 8)
    $pnlContent.Controls.Add($script:logBox)

    $btnBack.Enabled = $false
    $btnNext.Enabled = $false

    function Log($msg, $color) {
        if (-not $color) { $color = [System.Drawing.Color]::FromArgb(200,255,200) }
        $script:logBox.SelectionStart  = $script:logBox.TextLength
        $script:logBox.SelectionColor  = $color
        $script:logBox.AppendText("$msg`n")
        $script:logBox.ScrollToCaret()
        [System.Windows.Forms.Application]::DoEvents()
    }

    function LogOk($msg)   { Log "  ✔  $msg" ([System.Drawing.Color]::FromArgb(100,230,100)) }
    function LogErr($msg)  { Log "  ✖  $msg" ([System.Drawing.Color]::FromArgb(255,100,100)) }
    function LogInfo($msg) { Log "  ›  $msg" ([System.Drawing.Color]::FromArgb(180,220,255)) }

    Log "========================================" $BLUE
    Log "  MiniMart POS Ultimate - Installation" $BLUE
    Log "========================================" $BLUE

    # ── Step 1: Database setup ─────────────────────────────────────────────────
    Set-Progress 58
    Set-Status "Setting up database..."
    Log ""
    Log "[1/4] Database Setup"

    $skipDb   = $script:chkSkipDb.Checked
    $dbHost   = $script:txtDbHost.Text
    $dbPort   = $script:txtDbPort.Text
    $rootPass = $script:txtRootPass.Text
    $posPass  = $script:txtPosPass.Text

    $dbConfig["host"] = $dbHost
    $dbConfig["port"] = $dbPort
    $dbConfig["pass"] = $posPass

    if ($skipDb) {
        LogInfo "Skipping database setup (manual mode)."
    } else {
        LogInfo "Creating database user and schema..."
        try {
            # Create user SQL
            $createUserSql = "CREATE USER IF NOT EXISTS 'pos_user'@'localhost' IDENTIFIED BY '$posPass'; CREATE USER IF NOT EXISTS 'pos_user'@'%' IDENTIFIED BY '$posPass'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,INDEX ON minimart_pos.* TO 'pos_user'@'localhost'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,INDEX ON minimart_pos.* TO 'pos_user'@'%'; FLUSH PRIVILEGES;"
            $result = echo $createUserSql | & mysql -h$dbHost -P$dbPort -uroot "-p$rootPass" 2>&1
            LogOk "Database user 'pos_user' created."
        } catch {
            LogErr "Warning: Could not create DB user: $_"
        }

        try {
            $schemaFile = Join-Path $SCRIPT_DIR "src\main\resources\sql\schema.sql"
            $result = & mysql -h$dbHost -P$dbPort -uroot "-p$rootPass" --execute="source $schemaFile" 2>&1
            LogOk "Database schema imported."
        } catch {
            LogErr "Warning: Schema import issue: $_"
        }
    }

    # Write connection.properties
    $connFile = Join-Path $SCRIPT_DIR "src\main\resources\config\connection.properties"
    $connContent = @"
# MiniMart POS - Database Connection Configuration
db.host=$dbHost
db.port=$dbPort
db.name=minimart_pos
db.user=pos_user
db.password=$posPass

sync.multicast.group=239.255.1.1
sync.multicast.port=45678
sync.enabled=true
"@
    Set-Content -Path $connFile -Value $connContent
    LogOk "Connection properties configured."

    # ── Step 2: Build ──────────────────────────────────────────────────────────
    Set-Progress 65
    Set-Status "Building application (2-3 minutes)..."
    Log ""
    Log "[2/4] Building Application"
    LogInfo "Running: mvn clean package  (first run downloads dependencies ~100MB)..."

    try {
        $buildResult = & mvn clean package -q "-f" "$SCRIPT_DIR\pom.xml" 2>&1
        if ($LASTEXITCODE -eq 0) {
            LogOk "Build successful."
            Set-Progress 85
        } else {
            LogErr "Build failed! Output:"
            Log $buildResult ([System.Drawing.Color]::FromArgb(255,150,100))
            Set-Status "Build failed"
            $btnBack.Enabled = $true
            return
        }
    } catch {
        LogErr "Build error: $_"
        $btnBack.Enabled = $true
        return
    }

    # ── Step 3: Copy to install dir ────────────────────────────────────────────
    Set-Progress 88
    Set-Status "Installing files..."
    Log ""
    Log "[3/4] Installing Files"

    if (-not (Test-Path $INSTALL_DIR)) {
        New-Item -ItemType Directory -Path $INSTALL_DIR | Out-Null
    }

    # Copy the JAR
    $jarSource = Join-Path $SCRIPT_DIR "target\$JAR_NAME"
    $jarDest   = Join-Path $INSTALL_DIR $JAR_NAME
    Copy-Item $jarSource $jarDest -Force
    LogOk "Application JAR installed to: $INSTALL_DIR"

    # Copy connection.properties so app can find it at runtime
    $configDir = Join-Path $INSTALL_DIR "config"
    if (-not (Test-Path $configDir)) { New-Item -ItemType Directory -Path $configDir | Out-Null }
    Copy-Item $connFile (Join-Path $configDir "connection.properties") -Force

    # ── Step 4: Shortcuts ──────────────────────────────────────────────────────
    Set-Progress 92
    Set-Status "Creating shortcuts..."
    Log ""
    Log "[4/4] Creating Shortcuts"

    # Create launcher script in install dir
    $launcherContent = "@echo off`r`ncd /d `"$INSTALL_DIR`"`r`njava -jar `"$jarDest`"`r`n"
    $launcherPath = Join-Path $INSTALL_DIR "MiniMartPOS.bat"
    Set-Content -Path $launcherPath -Value $launcherContent

    # Desktop shortcut
    try {
        $WScriptShell = New-Object -ComObject WScript.Shell
        $desktopPath  = [System.Environment]::GetFolderPath("Desktop")
        $shortcut     = $WScriptShell.CreateShortcut("$desktopPath\MiniMart POS.lnk")
        $shortcut.TargetPath       = "java"
        $shortcut.Arguments        = "-jar `"$jarDest`""
        $shortcut.WorkingDirectory = $INSTALL_DIR
        $shortcut.Description      = "MiniMart POS Ultimate v$APP_VERSION"
        $shortcut.Save()
        LogOk "Desktop shortcut created."
    } catch {
        LogErr "Could not create desktop shortcut: $_"
    }

    # Start Menu shortcut
    try {
        $startMenuPath = Join-Path ([System.Environment]::GetFolderPath("StartMenu")) "Programs\MiniMart POS"
        if (-not (Test-Path $startMenuPath)) { New-Item -ItemType Directory -Path $startMenuPath | Out-Null }
        $shortcut2     = $WScriptShell.CreateShortcut("$startMenuPath\MiniMart POS Ultimate.lnk")
        $shortcut2.TargetPath       = "java"
        $shortcut2.Arguments        = "-jar `"$jarDest`""
        $shortcut2.WorkingDirectory = $INSTALL_DIR
        $shortcut2.Description      = "MiniMart POS Ultimate v$APP_VERSION"
        $shortcut2.Save()
        LogOk "Start Menu shortcut created."
    } catch {
        LogErr "Could not create Start Menu shortcut: $_"
    }

    # Register in Add/Remove Programs
    try {
        $regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\MiniMartPOS"
        New-Item -Path $regPath -Force | Out-Null
        Set-ItemProperty $regPath "DisplayName"     "$APP_NAME"
        Set-ItemProperty $regPath "DisplayVersion"  $APP_VERSION
        Set-ItemProperty $regPath "Publisher"       "MiniMart"
        Set-ItemProperty $regPath "InstallLocation" $INSTALL_DIR
        Set-ItemProperty $regPath "UninstallString" "java -jar `"$jarDest`""
        LogOk "Registered in Add/Remove Programs."
    } catch {}

    Set-Progress 100
    Set-Status "Installation complete!"
    Log ""
    Log "========================================" ([System.Drawing.Color]::FromArgb(100,230,100))
    Log "  Installation Complete!" ([System.Drawing.Color]::FromArgb(100,230,100))
    Log "========================================" ([System.Drawing.Color]::FromArgb(100,230,100))
    Log "  Username: admin" ([System.Drawing.Color]::FromArgb(255,220,100))
    Log "  Password: Admin@123" ([System.Drawing.Color]::FromArgb(255,220,100))
    Log "  Change password after first login!" ([System.Drawing.Color]::FromArgb(255,180,80))

    $script:installOk = $true
    $btnNext.Text    = "Finish"
    $btnNext.Enabled = $true
}

# ── PAGE 4: Finish ────────────────────────────────────────────────────────────
function Show-FinishPage {
    $pnlContent.Controls.Clear()
    Set-ActiveStep 4
    Set-Progress 100

    Add-SectionTitle $pnlContent "Installation Complete! 🎉" 20

    $bigLabel = New-Object System.Windows.Forms.Label
    $bigLabel.Text      = "✔"
    $bigLabel.ForeColor = $GREEN
    $bigLabel.Font      = New-Object System.Drawing.Font("Segoe UI", 48)
    $bigLabel.AutoSize  = $true
    $bigLabel.Location  = New-Object System.Drawing.Point(185, 50)
    $pnlContent.Controls.Add($bigLabel)

    Add-Info $pnlContent "MiniMart POS Ultimate has been installed." 130 $DARK
    Add-Info $pnlContent "A shortcut has been added to your Desktop and Start Menu." 152 [System.Drawing.Color]::Gray

    $box = New-Object System.Windows.Forms.Panel
    $box.Location  = New-Object System.Drawing.Point(20, 185)
    $box.Size      = New-Object System.Drawing.Size(410, 78)
    $box.BackColor = [System.Drawing.Color]::FromArgb(232,245,233)
    $pnlContent.Controls.Add($box)
    $loginInfo = New-Object System.Windows.Forms.Label
    $loginInfo.Text = "Default Login:`n  Username:  admin`n  Password:  Admin@123`n  ⚠ Change password immediately after first login!"
    $loginInfo.Font      = New-Object System.Drawing.Font("Segoe UI", 9)
    $loginInfo.ForeColor = $DARK
    $loginInfo.AutoSize  = $false
    $loginInfo.Size      = New-Object System.Drawing.Size(400, 68)
    $loginInfo.Location  = New-Object System.Drawing.Point(8, 6)
    $box.Controls.Add($loginInfo)

    $script:chkLaunch = New-Object System.Windows.Forms.CheckBox
    $script:chkLaunch.Text     = "Launch MiniMart POS now"
    $script:chkLaunch.Checked  = $true
    $script:chkLaunch.Font     = New-Object System.Drawing.Font("Segoe UI", 9)
    $script:chkLaunch.AutoSize = $true
    $script:chkLaunch.Location = New-Object System.Drawing.Point(20, 278)
    $pnlContent.Controls.Add($script:chkLaunch)

    $btnBack.Enabled = $false
    $btnNext.Text    = "Finish"
    $btnNext.Enabled = $true
    $btnCancel.Enabled = $false
}

# ==============================================================================
# NAVIGATION
# ==============================================================================

$pages = @(
    { Show-WelcomePage },
    { Show-RequirementsPage },
    { Show-DatabasePage },
    { Show-InstallPage },
    { Show-FinishPage }
)

function Go-Next {
    if ($script:currentPage -eq 3 -and $script:installOk) {
        $script:currentPage = 4
        & $pages[4]
        return
    }
    if ($script:currentPage -eq 4) {
        # Finish
        if ($script:chkLaunch -and $script:chkLaunch.Checked) {
            $jarPath = Join-Path $INSTALL_DIR $JAR_NAME
            Start-Process "java" "-jar `"$jarPath`"" -WorkingDirectory $INSTALL_DIR
        }
        $form.Close()
        return
    }
    $script:currentPage++
    & $pages[$script:currentPage]
}

function Go-Back {
    if ($script:currentPage -gt 0) {
        $script:currentPage--
        & $pages[$script:currentPage]
    }
}

$btnNext.Add_Click({ Go-Next })
$btnBack.Add_Click({ Go-Back })
$btnCancel.Add_Click({
    if ([System.Windows.Forms.MessageBox]::Show(
        "Cancel installation?", "MiniMart POS Setup",
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::Question) -eq "Yes") {
        $form.Close()
    }
})

# ── Start ─────────────────────────────────────────────────────────────────────
& $pages[0]
$form.Add_Shown({ $form.Activate() })
[System.Windows.Forms.Application]::Run($form)

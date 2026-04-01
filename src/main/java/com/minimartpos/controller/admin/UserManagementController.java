package com.minimartpos.controller.admin;

import com.minimartpos.model.User;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.model.enums.Role;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AuthService;
import com.minimartpos.service.SettingsService;
import com.minimartpos.service.UserService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin screen for managing users.
 *
 * Features:
 * - Table of all users with search + role filter
 * - Inline edit panel: basic info form + live password strength
 * - Cashier permission matrix grouped by category
 * - Permission templates (Trainee / Regular / Senior / Supervisor)
 * - Enable / disable accounts
 * - Password reset (generates temp password)
 * - Delete user (with self-delete protection)
 */
public class UserManagementController implements Initializable {

    private static final Logger logger = LogManager.getLogger(UserManagementController.class);

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML
    private Label sidebarUserLabel;
    @FXML
    private Label sidebarCompanyLabel;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> roleFilter;
    @FXML
    private Label userCountLabel;

    // Table
    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, String> colName;
    @FXML
    private TableColumn<User, String> colUsername;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private TableColumn<User, String> colStatus;
    @FXML
    private TableColumn<User, String> colLastLogin;
    @FXML
    private TableColumn<User, String> colPerms;
    @FXML
    private TableColumn<User, String> colActions;

    // Edit panel
    @FXML
    private VBox editPanel;
    @FXML
    private Label editPanelTitle;
    @FXML
    private TextField fieldFullName;
    @FXML
    private TextField fieldUsername;
    @FXML
    private ComboBox<Role> fieldRole;
    @FXML
    private TextField fieldTimeout;
    @FXML
    private TextField fieldCashLimit;
    @FXML
    private TextField fieldDailyTarget;
    @FXML
    private TextField fieldEmail;
    @FXML
    private TextField fieldPhone;
    @FXML
    private VBox passwordSection;
    @FXML
    private PasswordField fieldPassword;
    @FXML
    private Label passwordStrengthLabel;
    @FXML
    private CheckBox fieldActive;
    @FXML
    private Button resetPwdBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private Button saveBtn;
    @FXML
    private Label formErrorLabel;
    @FXML
    private VBox permissionsSection;
    @FXML
    private VBox permissionGroupsBox;

    // ── State ─────────────────────────────────────────────────────────────────
    private final UserService userService = new UserService();
    private final SettingsService settingsService = new SettingsService();
    private final AuthService authService = new AuthService();
    private final ObservableList<User> allUsers = FXCollections.observableArrayList();
    private FilteredList<User> filteredUsers;
    private User editingUser = null; // null = new user
    private final Map<Permission, CheckBox> permCheckboxes = new LinkedHashMap<>();

    // ── Permission Templates ──────────────────────────────────────────────────
    private static final Set<Permission> TEMPLATE_TRAINEE = Set.of(
            Permission.VIEW_LOW_STOCK_ALERTS);
    private static final Set<Permission> TEMPLATE_REGULAR = Set.of(
            Permission.VIEW_LOW_STOCK_ALERTS,
            Permission.APPLY_PERCENTAGE_DISCOUNT,
            Permission.APPLY_FIXED_DISCOUNT,
            Permission.APPLY_LINE_ITEM_DISCOUNT,
            Permission.APPLY_BILL_DISCOUNT,
            Permission.ADD_CUSTOMER,
            Permission.REPRINT_OLD_BILL);
    private static final Set<Permission> TEMPLATE_SENIOR = new HashSet<>(TEMPLATE_REGULAR) {
        {
            add(Permission.CHANGE_SELLING_PRICE);
            add(Permission.VOID_BILL);
            add(Permission.DELETE_OWN_BILL);
            add(Permission.VIEW_CUSTOMER_CREDIT);
            add(Permission.EDIT_CUSTOMER);
            add(Permission.ACCESS_BASIC_REPORTS);
            add(Permission.VIEW_BILL_PROFIT);
        }
    };
    private static final Set<Permission> TEMPLATE_SUPERVISOR = EnumSet.allOf(Permission.class);

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());
        setupRoleFilter();
        setupTableColumns();
        buildPermissionMatrix();
        refreshUsers();
        logger.info("UserManagement screen initialized");
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupRoleFilter() {
        roleFilter.setItems(FXCollections.observableArrayList("All Roles", "ADMIN", "CASHIER"));
        roleFilter.getSelectionModel().selectFirst();
        fieldRole.setItems(FXCollections.observableArrayList(Role.values()));
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));

        colRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().getDisplayName()));
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(v);
                setStyle(v.equals("Administrator")
                        ? "-fx-text-fill:-pos-primary; -fx-font-weight:bold;"
                        : "-fx-text-fill:-pos-text-secondary;");
            }
        });

        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Disabled"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(v);
                setStyle(v.equals("Active")
                        ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                        : "-fx-text-fill:-pos-danger;");
            }
        });

        colLastLogin.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastLogin() != null
                        ? DateUtil.formatDateTime(c.getValue().getLastLogin())
                        : "Never"));

        colPerms.setCellValueFactory(c -> {
            User u = c.getValue();
            if (u.getRole() == Role.ADMIN)
                return new SimpleStringProperty("All");
            return new SimpleStringProperty(u.getPermissions().size() + " granted");
        });

        // Actions column: Edit / Disable / Delete buttons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏ Edit");
            private final Button toggleBtn = new Button();
            private final Button deleteBtnStatus = new Button("🗑");
            private final HBox box = new HBox(4, editBtn, toggleBtn, deleteBtnStatus);
            {
                box.setAlignment(Pos.CENTER);
                editBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                        "-fx-background-color:-pos-primary; -fx-text-fill:white; " +
                        "-fx-background-radius:4;");
                toggleBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                        "-fx-background-radius:4;");
                deleteBtnStatus.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                        "-fx-background-radius:4; -fx-background-color:#FFEBEE; -fx-text-fill:-pos-danger;");

                editBtn.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    openEditPanel(u);
                });
                toggleBtn.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    toggleUserActive(u);
                });
                deleteBtnStatus.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    final int userId = u.getId();
                    final String username = u.getUsername();
                    if (userId == SessionManager.getCurrentUser().getId()) {
                        AlertUtil.showWarning("Cannot Delete", "You cannot delete your own account.");
                        return;
                    }
                    if (AlertUtil.confirm("Delete User", "Delete user '" + username + "'? This cannot be undone.")) {
                        if (userService.deleteUser(userId)) {
                            AlertUtil.showInfo("Deleted", "User deleted successfully.");
                            refreshUsers();
                        } else {
                            AlertUtil.showError("Error", "Could not delete user.");
                        }
                    }
                });
            }

            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                User u = getTableView().getItems().get(getIndex());
                toggleBtn.setText(u.isActive() ? "⛔ Disable" : "✅ Enable");
                toggleBtn.setStyle(toggleBtn.getStyle() +
                        (u.isActive()
                                ? " -fx-background-color:#FFF3E0; -fx-text-fill:#EF6C00;"
                                : " -fx-background-color:#E8F5E9; -fx-text-fill:-pos-success;"));
                setGraphic(box);
            }
        });
    }

    private void buildPermissionMatrix() {
        permissionGroupsBox.getChildren().clear();
        permCheckboxes.clear();

        // Group permissions by category
        Map<String, List<Permission>> grouped = new LinkedHashMap<>();
        for (Permission p : Permission.values()) {
            grouped.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(p);
        }

        for (Map.Entry<String, List<Permission>> entry : grouped.entrySet()) {
            VBox groupBox = new VBox(6);
            groupBox.setStyle("-fx-background-color:-pos-surface-alt; -fx-background-radius:6; " +
                    "-fx-border-color:-pos-border; -fx-border-radius:6; -fx-padding:10;");

            // Category header with select-all toggle
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            CheckBox groupToggle = new CheckBox(entry.getKey());
            groupToggle.setStyle("-fx-font-weight:bold; -fx-font-size:12px; -fx-text-fill: -pos-text-primary;");
            header.getChildren().add(groupToggle);
            groupBox.getChildren().add(header);

            VBox itemsBox = new VBox(4);
            itemsBox.setStyle("-fx-padding:4 0 0 10;");
            List<CheckBox> groupBoxes = new ArrayList<>();

            for (Permission perm : entry.getValue()) {
                CheckBox cb = new CheckBox(perm.getDescription());
                cb.setStyle("-fx-font-size:12px; -fx-text-fill: -pos-text-primary;");
                cb.setWrapText(true);
                permCheckboxes.put(perm, cb);
                groupBoxes.add(cb);
                itemsBox.getChildren().add(cb);
            }

            // Group toggle selects/deselects all in group
            groupToggle.setOnAction(e -> {
                boolean sel = groupToggle.isSelected();
                groupBoxes.forEach(cb -> cb.setSelected(sel));
            });
            // Update group toggle when individual items change
            groupBoxes.forEach(cb -> cb.selectedProperty().addListener((obs, o, n) -> {
                long selected = groupBoxes.stream().filter(CheckBox::isSelected).count();
                groupToggle.setIndeterminate(selected > 0 && selected < groupBoxes.size());
                groupToggle.setSelected(selected == groupBoxes.size());
            }));

            groupBox.getChildren().add(itemsBox);
            permissionGroupsBox.getChildren().add(groupBox);
        }
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    @FXML
    public void refreshUsers() {
        allUsers.setAll(userService.getAllUsers());
        filteredUsers = new FilteredList<>(allUsers, u -> true);
        userTable.setItems(filteredUsers);
        applyFilters();
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String role = roleFilter.getValue();

        filteredUsers.setPredicate(u -> {
            boolean matchSearch = search.isEmpty()
                    || u.getFullName().toLowerCase().contains(search)
                    || u.getUsername().toLowerCase().contains(search);
            boolean matchRole = role == null || role.equals("All Roles")
                    || u.getRole().name().equals(role);
            return matchSearch && matchRole;
        });
        userCountLabel.setText(filteredUsers.size() + " user" +
                (filteredUsers.size() == 1 ? "" : "s"));
    }

    @FXML
    private void onSearchChanged() {
        applyFilters();
    }

    @FXML
    private void onRoleFilterChanged() {
        applyFilters();
    }

    // ── Edit Panel ────────────────────────────────────────────────────────────

    private void openEditPanel(User user) {
        editingUser = user;
        editPanelTitle.setText("Edit User");

        // Load permissions from DB
        userService.findById(user.getId()).ifPresent(u -> {
            editingUser = u;
            populateForm(u);
        });

        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void openAddUserDialog() {
        editingUser = null;
        editPanelTitle.setText("Add New User");
        clearForm();
        passwordSection.setVisible(true);
        passwordSection.setManaged(true);
        resetPwdBtn.setVisible(false);
        resetPwdBtn.setManaged(false);
        deleteBtn.setVisible(false);
        deleteBtn.setManaged(false);
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void closeEditPanel() {
        editPanel.setVisible(false);
        editPanel.setManaged(false);
        editingUser = null;
        clearFormError();
    }

    private void populateForm(User u) {
        fieldFullName.setText(u.getFullName());
        fieldUsername.setText(u.getUsername());
        fieldRole.setValue(u.getRole());
        fieldTimeout.setText(String.valueOf(u.getSessionTimeoutMinutes()));
        fieldCashLimit.setText(u.getCashLimit() != null
                ? u.getCashLimit().toPlainString()
                : "0");
        fieldDailyTarget.setText(u.getDailySalesTarget() != null
                ? u.getDailySalesTarget().toPlainString()
                : "0");
        fieldEmail.setText(u.getEmail() != null ? u.getEmail() : "");
        fieldPhone.setText(u.getPhone() != null ? u.getPhone() : "");
        fieldActive.setSelected(u.isActive());
        fieldPassword.clear();

        // Password section: show but optional for edits
        passwordSection.setVisible(true);
        passwordSection.setManaged(true);
        resetPwdBtn.setVisible(true);
        resetPwdBtn.setManaged(true);
        deleteBtn.setVisible(true);
        deleteBtn.setManaged(true);

        // Can't delete self
        deleteBtn.setDisable(u.getId() == SessionManager.getCurrentUser().getId());

        updatePermissionVisibility(u.getRole());
        populatePermissions(u.getPermissions());
        clearFormError();
    }

    private void clearForm() {
        fieldFullName.clear();
        fieldUsername.clear();
        fieldRole.setValue(Role.CASHIER);
        fieldTimeout.setText("30");
        fieldCashLimit.setText("0");
        fieldDailyTarget.setText("0");
        fieldEmail.clear();
        fieldPhone.clear();
        fieldPassword.clear();
        fieldActive.setSelected(true);
        updatePermissionVisibility(Role.CASHIER);
        permCheckboxes.values().forEach(cb -> cb.setSelected(false));
        clearFormError();
    }

    private void populatePermissions(Set<Permission> permissions) {
        permCheckboxes.forEach((perm, cb) -> cb.setSelected(permissions.contains(perm)));
    }

    @FXML
    private void onRoleChanged() {
        Role selected = fieldRole.getValue();
        if (selected != null)
            updatePermissionVisibility(selected);
    }

    private void updatePermissionVisibility(Role role) {
        boolean isCashier = role == Role.CASHIER;
        permissionsSection.setVisible(isCashier);
        permissionsSection.setManaged(isCashier);
    }

    // ── Save / Delete / Reset ─────────────────────────────────────────────────

    @FXML
    private void saveUser() {
        clearFormError();

        // Validate
        String fullName = fieldFullName.getText().trim();
        String username = fieldUsername.getText().trim();
        String password = fieldPassword.getText();
        Role role = fieldRole.getValue();

        if (fullName.isEmpty()) {
            showFormError("Full name is required.");
            return;
        }
        if (username.isEmpty()) {
            showFormError("Username is required.");
            return;
        }
        if (username.length() < 3) {
            showFormError("Username must be at least 3 characters.");
            return;
        }
        if (role == null) {
            showFormError("Please select a role.");
            return;
        }
        if (editingUser == null && password.isEmpty()) {
            showFormError("Password is required for new users.");
            return;
        }
        // No minimum strength requirement — any password is accepted

        int timeout = 30;
        try {
            timeout = Integer.parseInt(fieldTimeout.getText().trim());
        } catch (NumberFormatException ignored) {
        }

        java.math.BigDecimal cashLimit = java.math.BigDecimal.ZERO;
        try {
            cashLimit = new java.math.BigDecimal(fieldCashLimit.getText().trim());
        } catch (NumberFormatException ignored) {
        }

        java.math.BigDecimal dailyTarget = java.math.BigDecimal.ZERO;
        try {
            dailyTarget = new java.math.BigDecimal(fieldDailyTarget.getText().trim());
        } catch (NumberFormatException ignored) {
        }

        // Build/update user object
        User u = editingUser != null ? editingUser : new User();
        u.setFullName(fullName);
        u.setUsername(username);
        u.setRole(role);
        u.setEmail(fieldEmail.getText().trim());
        u.setPhone(fieldPhone.getText().trim());
        u.setActive(fieldActive.isSelected());
        u.setSessionTimeoutMinutes(timeout);
        u.setCashLimit(cashLimit);
        u.setDailySalesTarget(dailyTarget);

        // Collect permissions if cashier
        if (role == Role.CASHIER) {
            Set<Permission> selected = permCheckboxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
            u.setPermissions(selected);
        }

        boolean success;
        if (editingUser == null) {
            // Create new user
            int newId = userService.createUser(u, password);
            success = newId > 0;
            if (success) {
                // Save permissions for new cashier
                if (role == Role.CASHIER) {
                    userService.savePermissions(newId, u.getPermissions());
                }
            } else {
                showFormError("Username already exists or save failed.");
                return;
            }
        } else {
            // Update existing
            success = userService.updateUser(u);
            if (success) {
                // Admin password override
                if (!password.isEmpty()) {
                    userService.adminResetPassword(u.getId(), password);
                }
                // Update permissions separately
                if (role == Role.CASHIER) {
                    userService.savePermissions(u.getId(), u.getPermissions());
                }
            }
        }

        if (success) {
            AlertUtil.showInfo("Saved", "User '" + username + "' saved successfully.");
            refreshUsers();
            closeEditPanel();
        } else {
            showFormError("Failed to save user. Please try again.");
        }
    }

    @FXML
    private void deleteUser() {
        if (editingUser == null)
            return;
        final int userId = editingUser.getId();
        final String username = editingUser.getUsername();
        if (userId == SessionManager.getCurrentUser().getId()) {
            AlertUtil.showWarning("Cannot Delete", "You cannot delete your own account.");
            return;
        }
        if (AlertUtil.confirm("Delete User",
                "Delete user '" + username + "'? This cannot be undone.")) {
            if (userService.deleteUser(userId)) {
                AlertUtil.showInfo("Deleted", "User deleted successfully.");
                refreshUsers();
                closeEditPanel();
            } else {
                AlertUtil.showError("Error", "Could not delete user.");
            }
        }
    }

    @FXML
    private void resetPassword() {
        if (editingUser == null)
            return;
        if (AlertUtil.confirm("Reset Password",
                "Generate a new temporary password for '" + editingUser.getUsername() + "'?")) {
            String tempPwd = userService.resetPassword(editingUser.getId());
            AlertUtil.showInfo("Password Reset",
                    "Temporary password: " + tempPwd
                            + "\n\nGive this to the user and ask them to change it immediately.");
        }
    }

    private void toggleUserActive(User u) {
        if (u.getId() == SessionManager.getCurrentUser().getId()) {
            AlertUtil.showWarning("Cannot Disable", "You cannot disable your own account.");
            return;
        }
        boolean newState = !u.isActive();
        String action = newState ? "enable" : "disable";
        if (AlertUtil.confirm((newState ? "Enable" : "Disable") + " User",
                "Are you sure you want to " + action + " user '" + u.getUsername() + "'?")) {
            userService.setActive(u.getId(), newState);
            refreshUsers();
        }
    }

    // ── Permission Templates ──────────────────────────────────────────────────

    @FXML
    private void grantAllPermissions() {
        applyTemplate(TEMPLATE_SUPERVISOR);
    }

    @FXML
    private void revokeAllPermissions() {
        permCheckboxes.values().forEach(cb -> cb.setSelected(false));
    }

    @FXML
    private void applyTemplateTrainee() {
        applyTemplate(TEMPLATE_TRAINEE);
    }

    @FXML
    private void applyTemplateRegular() {
        applyTemplate(TEMPLATE_REGULAR);
    }

    @FXML
    private void applyTemplateSenior() {
        applyTemplate(TEMPLATE_SENIOR);
    }

    @FXML
    private void applyTemplateSupervisor() {
        applyTemplate(TEMPLATE_SUPERVISOR);
    }

    private void applyTemplate(Set<Permission> template) {
        permCheckboxes.forEach((perm, cb) -> cb.setSelected(template.contains(perm)));
    }

    // ── Password Strength Live Indicator ──────────────────────────────────────

    @FXML
    private void onPasswordChanged() {
        String pwd = fieldPassword.getText();
        if (pwd.isEmpty()) {
            passwordStrengthLabel.setText("");
            return;
        }
        String strength = authService.getPasswordStrength(pwd);
        String[] display = switch (strength) {
            case "WEAK" -> new String[] { "⚠ Weak", "-fx-text-fill:-pos-danger;" };
            case "FAIR" -> new String[] { "◑ Fair", "-fx-text-fill:-pos-warning;" };
            case "STRONG" -> new String[] { "✔ Strong", "-fx-text-fill:-pos-success;" };
            case "VERY_STRONG" ->
                new String[] { "✔✔ Very Strong", "-fx-text-fill:-pos-success; -fx-font-weight:bold;" };
            default -> new String[] { "", "" };
        };
        passwordStrengthLabel.setText(display[0]);
        passwordStrengthLabel.setStyle(display[1] + " -fx-font-size:11px;");
    }
    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showFormError(String msg) {
        formErrorLabel.setText(msg);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void clearFormError() {
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML
    private void navigateToDashboard() {
        com.minimartpos.util.SceneManager.navigateTo("admin/AdminDashboard.fxml");
    }

    @FXML
    private void navigateToPOS() {
        com.minimartpos.util.SceneManager.navigateTo("cashier/POSTerminal.fxml");
    }

    @FXML
    private void navigateToUsers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/UserManagement.fxml");
    }

    @FXML
    private void navigateToProducts() {
        com.minimartpos.util.SceneManager.navigateTo("admin/ProductManagement.fxml");
    }

    @FXML
    private void navigateToCustomers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/CustomerManagement.fxml");
    }

    @FXML
    private void navigateToSuppliers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/SupplierManagement.fxml");
    }

    @FXML
    private void navigateToCashierMonitor() {
        com.minimartpos.util.SceneManager.navigateTo("admin/CashierMonitor.fxml");
    }

    @FXML
    private void navigateToBills() {
        com.minimartpos.util.SceneManager.navigateTo("admin/BillHistory.fxml");
    }

    @FXML
    private void navigateToStock() {
        com.minimartpos.util.SceneManager.navigateTo("admin/StockAdjustment.fxml");
    }

    @FXML
    private void navigateToReports() {
        com.minimartpos.util.SceneManager.navigateTo("admin/Reports.fxml");
    }

    @FXML
    private void navigateToAudit() {
        com.minimartpos.util.SceneManager.navigateTo("admin/AuditLog.fxml");
    }

    @FXML
    private void navigateToSettings() {
        com.minimartpos.util.SceneManager.navigateTo("admin/Settings.fxml");
    }

}

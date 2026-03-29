package com.minimartpos.service;

import com.minimartpos.model.User;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.repository.UserRepository;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Business logic for user management (admin-facing).
 */
public class UserService {

    private static final Logger logger = LogManager.getLogger(UserService.class);

    private final UserRepository userRepo     = new UserRepository();
    private final AuthService    authService  = new AuthService();
    private final AuditService   auditService = new AuditService();

    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    public Optional<User> findById(int id) {
        return userRepo.findById(id);
    }

    /**
     * Creates a new user. Hashes the password, validates uniqueness.
     * @return the new user's ID, or -1 on failure.
     */
    public int createUser(User user, String plainPassword) {
        if (userRepo.findByUsername(user.getUsername()).isPresent()) {
            logger.warn("createUser: username '{}' already exists", user.getUsername());
            return -1;
        }
        user.setPasswordHash(authService.hashPassword(plainPassword));
        int id = userRepo.insert(user);
        if (id > 0) {
            auditService.log("USER_CREATE", "users", id, null,
                             "username=" + user.getUsername() + " role=" + user.getRole());
            logger.info("User created: id={} username={}", id, user.getUsername());
        }
        return id;
    }

    public boolean updateUser(User user) {
        boolean ok = userRepo.update(user);
        if (ok) auditService.log("USER_UPDATE", "users", user.getId(),
                                 null, "fullName=" + user.getFullName());
        return ok;
    }

    public boolean setActive(int userId, boolean active) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return false;
        User u = opt.get();
        u.setActive(active);
        boolean ok = userRepo.update(u);
        if (ok) auditService.log(active ? "USER_ENABLE" : "USER_DISABLE", "users", userId, null, null);
        return ok;
    }

    public void savePermissions(int userId, Set<Permission> permissions) {
        userRepo.savePermissions(userId, permissions, SessionManager.getCurrentUser().getId());
        auditService.log("PERMISSION_UPDATE", "users", userId,
                         null, permissions.size() + " permissions assigned");
        logger.info("Permissions updated for userId={} by {}",
                    userId, SessionManager.getCurrentUser().getUsername());
    }

    public String resetPassword(int userId) {
        String tempPwd = authService.resetPassword(userId);
        auditService.log("PASSWORD_RESET", "users", userId, null, "temp password issued");
        return tempPwd;
    }

    public boolean deleteUser(int userId) {
        if (userId == SessionManager.getCurrentUser().getId()) {
            logger.warn("deleteUser: attempt to delete self (userId={})", userId);
            return false;
        }
        boolean ok = userRepo.delete(userId);
        if (ok) {
            auditService.log("USER_DELETE", "users", userId, null, null);
            logger.info("User deleted: userId={} by {}",
                        userId, SessionManager.getCurrentUser().getUsername());
        }
        return ok;
    }
}

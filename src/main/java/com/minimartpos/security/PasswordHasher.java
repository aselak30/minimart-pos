package com.minimartpos.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.minimartpos.config.AppConfig;

/**
 * Standalone BCrypt password hashing utility.
 * Use this when you need hashing outside of AuthService context
 * (e.g. in setup scripts or tests).
 */
public final class PasswordHasher {

    private PasswordHasher() {}

    public static String hash(String plainPassword) {
        return BCrypt.withDefaults()
                     .hashToString(AppConfig.BCRYPT_STRENGTH, plainPassword.toCharArray());
    }

    public static boolean verify(String plainPassword, String hash) {
        return BCrypt.verifyer()
                     .verify(plainPassword.toCharArray(), hash)
                     .verified;
    }
}

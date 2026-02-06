package com.loopers.infrastructure.security;

import com.loopers.domain.service.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class Sha256PasswordEncoder implements PasswordEncoder {

    @Override
    public String encrypt(String rawPassword) {
        String salt = generateSalt();
        String hashed = sha256(rawPassword + salt);
        return salt + ":" + hashed;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        String[] parts = encodedPassword.split(":");
        if (parts.length != 2) {
            return false;
        }
        String salt = parts[0];
        String storedHash = parts[1];
        String inputHash = sha256(rawPassword + salt);
        return storedHash.equals(inputHash);
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 을 찾지 못했습니다.", e);
        }
    }
}

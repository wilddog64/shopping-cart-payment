package com.shoppingcart.payment.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption service for PCI DSS compliant data protection.
 *
 * Uses AES-256-GCM for authenticated encryption.
 * Encryption key should be loaded from Vault in production.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${payment.encryption.enabled:true}")
    private boolean enabled;

    @Value("${payment.encryption.key:}")
    private String encryptionKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (enabled && encryptionKeyBase64 != null && !encryptionKeyBase64.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("EncryptionService initialized with provided key");
        } else if (enabled) {
            // Generate a random key for development (NOT for production!)
            byte[] keyBytes = new byte[32]; // 256 bits
            secureRandom.nextBytes(keyBytes);
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.warn("EncryptionService using randomly generated key - NOT FOR PRODUCTION");
        }
    }

    /**
     * Encrypt sensitive data.
     *
     * @param plaintext The data to encrypt
     * @return Base64-encoded ciphertext with prepended IV
     */
    public String encrypt(String plaintext) {
        if (!enabled || plaintext == null) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt sensitive data.
     *
     * @param ciphertext Base64-encoded ciphertext with prepended IV
     * @return Decrypted plaintext
     */
    public String decrypt(String ciphertext) {
        if (!enabled || ciphertext == null) {
            return ciphertext;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Check if encryption is enabled.
     */
    public boolean isEnabled() {
        return enabled && secretKey != null;
    }
}

package com.shoppingcart.payment.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncryptionService Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "enabled", true);
        // Generate a valid 256-bit (32 bytes) key
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        ReflectionTestUtils.setField(encryptionService, "encryptionKeyBase64", keyBase64);
        encryptionService.init();
    }

    @Nested
    @DisplayName("encrypt")
    class Encrypt {

        @Test
        @DisplayName("should encrypt plaintext successfully")
        void shouldEncryptPlaintextSuccessfully() {
            // Arrange
            String plaintext = "4242424242424242";

            // Act
            String ciphertext = encryptionService.encrypt(plaintext);

            // Assert
            assertThat(ciphertext).isNotNull();
            assertThat(ciphertext).isNotEqualTo(plaintext);
            assertThat(ciphertext).isBase64(); // Should be base64 encoded
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNullWhenInputIsNull() {
            // Act
            String result = encryptionService.encrypt(null);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should produce different ciphertext for same plaintext")
        void shouldProduceDifferentCiphertextForSamePlaintext() {
            // Arrange
            String plaintext = "sensitive data";

            // Act
            String ciphertext1 = encryptionService.encrypt(plaintext);
            String ciphertext2 = encryptionService.encrypt(plaintext);

            // Assert - Different IVs should produce different ciphertexts
            assertThat(ciphertext1).isNotEqualTo(ciphertext2);
        }

        @Test
        @DisplayName("should return plaintext when encryption is disabled")
        void shouldReturnPlaintextWhenEncryptionIsDisabled() {
            // Arrange
            ReflectionTestUtils.setField(encryptionService, "enabled", false);
            String plaintext = "not encrypted";

            // Act
            String result = encryptionService.encrypt(plaintext);

            // Assert
            assertThat(result).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            // Arrange
            String plaintext = "";

            // Act
            String ciphertext = encryptionService.encrypt(plaintext);

            // Assert
            assertThat(ciphertext).isNotNull();
        }

        @Test
        @DisplayName("should handle special characters")
        void shouldHandleSpecialCharacters() {
            // Arrange
            String plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

            // Act
            String ciphertext = encryptionService.encrypt(plaintext);

            // Assert
            assertThat(ciphertext).isNotNull();
            assertThat(ciphertext).isNotEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            // Arrange
            String plaintext = "日本語 中文 한국어 émoji 🔐";

            // Act
            String ciphertext = encryptionService.encrypt(plaintext);

            // Assert
            assertThat(ciphertext).isNotNull();
        }
    }

    @Nested
    @DisplayName("decrypt")
    class Decrypt {

        @Test
        @DisplayName("should decrypt ciphertext successfully")
        void shouldDecryptCiphertextSuccessfully() {
            // Arrange
            String plaintext = "4242424242424242";
            String ciphertext = encryptionService.encrypt(plaintext);

            // Act
            String decrypted = encryptionService.decrypt(ciphertext);

            // Assert
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNullWhenInputIsNull() {
            // Act
            String result = encryptionService.decrypt(null);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return ciphertext when decryption is disabled")
        void shouldReturnCiphertextWhenDecryptionIsDisabled() {
            // Arrange
            ReflectionTestUtils.setField(encryptionService, "enabled", false);
            String ciphertext = "some_ciphertext";

            // Act
            String result = encryptionService.decrypt(ciphertext);

            // Assert
            assertThat(result).isEqualTo(ciphertext);
        }

        @Test
        @DisplayName("should throw exception for invalid ciphertext")
        void shouldThrowExceptionForInvalidCiphertext() {
            // Arrange
            String invalidCiphertext = "not_valid_base64_ciphertext!!!";

            // Act & Assert
            assertThatThrownBy(() -> encryptionService.decrypt(invalidCiphertext))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw exception for tampered ciphertext")
        void shouldThrowExceptionForTamperedCiphertext() {
            // Arrange
            String plaintext = "sensitive data";
            String ciphertext = encryptionService.encrypt(plaintext);

            // Tamper with the ciphertext
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            decoded[decoded.length - 1] ^= 0xFF; // Flip bits in last byte
            String tampered = Base64.getEncoder().encodeToString(decoded);

            // Act & Assert
            assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Decryption failed");
        }
    }

    @Nested
    @DisplayName("Round-trip encryption")
    class RoundTrip {

        @Test
        @DisplayName("should encrypt and decrypt card number")
        void shouldEncryptAndDecryptCardNumber() {
            // Arrange
            String cardNumber = "4242424242424242";

            // Act
            String encrypted = encryptionService.encrypt(cardNumber);
            String decrypted = encryptionService.decrypt(encrypted);

            // Assert
            assertThat(decrypted).isEqualTo(cardNumber);
        }

        @Test
        @DisplayName("should encrypt and decrypt long text")
        void shouldEncryptAndDecryptLongText() {
            // Arrange
            String longText = "A".repeat(10000);

            // Act
            String encrypted = encryptionService.encrypt(longText);
            String decrypted = encryptionService.decrypt(encrypted);

            // Assert
            assertThat(decrypted).isEqualTo(longText);
        }

        @Test
        @DisplayName("should encrypt and decrypt JSON payload")
        void shouldEncryptAndDecryptJsonPayload() {
            // Arrange
            String json = "{\"cardNumber\":\"4242424242424242\",\"cvv\":\"123\"}";

            // Act
            String encrypted = encryptionService.encrypt(json);
            String decrypted = encryptionService.decrypt(encrypted);

            // Assert
            assertThat(decrypted).isEqualTo(json);
        }
    }

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        @Test
        @DisplayName("should return true when enabled with key")
        void shouldReturnTrueWhenEnabledWithKey() {
            // Assert
            assertThat(encryptionService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            // Arrange
            ReflectionTestUtils.setField(encryptionService, "enabled", false);

            // Assert
            assertThat(encryptionService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should return false when key is null")
        void shouldReturnFalseWhenKeyIsNull() {
            // Arrange
            ReflectionTestUtils.setField(encryptionService, "secretKey", null);

            // Assert
            assertThat(encryptionService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Key initialization")
    class KeyInitialization {

        @Test
        @DisplayName("should initialize with valid base64 key")
        void shouldInitializeWithValidBase64Key() {
            // Arrange
            EncryptionService service = new EncryptionService();
            ReflectionTestUtils.setField(service, "enabled", true);
            byte[] keyBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(keyBytes);
            String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", keyBase64);

            // Act
            service.init();

            // Assert
            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should generate random key when no key provided")
        void shouldGenerateRandomKeyWhenNoKeyProvided() {
            // Arrange
            EncryptionService service = new EncryptionService();
            ReflectionTestUtils.setField(service, "enabled", true);
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", "");

            // Act
            service.init();

            // Assert - Should still work with generated key
            String plaintext = "test";
            String encrypted = service.encrypt(plaintext);
            String decrypted = service.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }
}

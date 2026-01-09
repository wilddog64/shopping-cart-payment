package com.shoppingcart.payment.security;

import org.springframework.stereotype.Component;

/**
 * Utility for masking PCI sensitive data in logs.
 */
@Component
public class PciDataMasker {

    /**
     * Mask a card number, showing only last 4 digits.
     * Example: 4242424242424242 -> ************4242
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        return "*".repeat(cardNumber.length() - 4) + last4;
    }

    /**
     * Mask CVV completely.
     */
    public String maskCvv(String cvv) {
        return "***";
    }

    /**
     * Mask email, showing only first char and domain.
     * Example: user@example.com -> u***@example.com
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) {
            return "*" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * Mask a token, showing only first and last 4 chars.
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Mask an address.
     */
    public String maskAddress(String address) {
        if (address == null || address.length() < 5) {
            return "***";
        }
        return address.substring(0, 3) + "***";
    }
}

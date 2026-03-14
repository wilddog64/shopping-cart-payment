package com.shoppingcart.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Payment Service Application
 *
 * PCI DSS compliant payment service with multi-gateway support (Stripe, PayPal, Mock).
 * Runs in isolated namespace (shopping-cart-payments) with strict network policies.
 */
@SpringBootApplication
@EnableScheduling
@EnableMethodSecurity
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

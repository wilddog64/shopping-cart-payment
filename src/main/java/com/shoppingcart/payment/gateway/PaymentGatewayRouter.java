package com.shoppingcart.payment.gateway;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes payment requests to the appropriate gateway.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayRouter {

    private final List<PaymentGateway> gateways;

    @Value("${payment.gateway.default:mock}")
    private String defaultGatewayName;

    private final Map<String, PaymentGateway> gatewayMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (PaymentGateway gateway : gateways) {
            gatewayMap.put(gateway.getName(), gateway);
            log.info("Registered payment gateway: {} (enabled: {})",
                    gateway.getName(), gateway.isEnabled());
        }

        if (!gatewayMap.containsKey(defaultGatewayName)) {
            log.warn("Default gateway '{}' not found, using mock", defaultGatewayName);
            defaultGatewayName = "mock";
        }
    }

    /**
     * Get a gateway by name.
     */
    public PaymentGateway getGateway(String name) {
        PaymentGateway gateway = gatewayMap.get(name);
        if (gateway == null) {
            throw new IllegalArgumentException("Unknown gateway: " + name);
        }
        if (!gateway.isEnabled()) {
            throw new IllegalStateException("Gateway is not enabled: " + name);
        }
        return gateway;
    }

    /**
     * Get the default gateway.
     */
    public PaymentGateway getDefaultGateway() {
        return getGateway(defaultGatewayName);
    }

    /**
     * Get gateway or default if not specified.
     */
    public PaymentGateway getGatewayOrDefault(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultGateway();
        }
        return getGateway(name);
    }

    /**
     * Check if a gateway exists and is enabled.
     */
    public boolean isGatewayAvailable(String name) {
        PaymentGateway gateway = gatewayMap.get(name);
        return gateway != null && gateway.isEnabled();
    }

    /**
     * Get all enabled gateways.
     */
    public List<PaymentGateway> getEnabledGateways() {
        return gateways.stream()
                .filter(PaymentGateway::isEnabled)
                .toList();
    }
}

package com.shoppingcart.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGatewayRouter Tests")
class PaymentGatewayRouterTest {

    @Mock
    private PaymentGateway mockGateway;

    @Mock
    private PaymentGateway stripeGateway;

    @Mock
    private PaymentGateway paypalGateway;

    private PaymentGatewayRouter router;

    @BeforeEach
    void setUp() {
        when(mockGateway.getName()).thenReturn("mock");
        when(mockGateway.isEnabled()).thenReturn(true);

        when(stripeGateway.getName()).thenReturn("stripe");
        when(stripeGateway.isEnabled()).thenReturn(true);

        when(paypalGateway.getName()).thenReturn("paypal");
        when(paypalGateway.isEnabled()).thenReturn(false); // Disabled by default

        List<PaymentGateway> gateways = List.of(mockGateway, stripeGateway, paypalGateway);
        router = new PaymentGatewayRouter(gateways);
        ReflectionTestUtils.setField(router, "defaultGatewayName", "mock");
        router.init();
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should register all gateways")
        void shouldRegisterAllGateways() {
            // Assert
            assertThat(router.isGatewayAvailable("mock")).isTrue();
            assertThat(router.isGatewayAvailable("stripe")).isTrue();
            assertThat(router.isGatewayAvailable("paypal")).isFalse(); // Disabled
        }

        @Test
        @DisplayName("should fall back to mock when default not found")
        void shouldFallBackToMockWhenDefaultNotFound() {
            // Arrange
            ReflectionTestUtils.setField(router, "defaultGatewayName", "nonexistent");
            router.init();

            // Act
            PaymentGateway defaultGateway = router.getDefaultGateway();

            // Assert
            assertThat(defaultGateway.getName()).isEqualTo("mock");
        }
    }

    @Nested
    @DisplayName("getGateway")
    class GetGateway {

        @Test
        @DisplayName("should return gateway by name")
        void shouldReturnGatewayByName() {
            // Act
            PaymentGateway gateway = router.getGateway("mock");

            // Assert
            assertThat(gateway).isEqualTo(mockGateway);
        }

        @Test
        @DisplayName("should return stripe gateway by name")
        void shouldReturnStripeGatewayByName() {
            // Act
            PaymentGateway gateway = router.getGateway("stripe");

            // Assert
            assertThat(gateway).isEqualTo(stripeGateway);
        }

        @Test
        @DisplayName("should throw exception for unknown gateway")
        void shouldThrowExceptionForUnknownGateway() {
            // Act & Assert
            assertThatThrownBy(() -> router.getGateway("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown gateway");
        }

        @Test
        @DisplayName("should throw exception for disabled gateway")
        void shouldThrowExceptionForDisabledGateway() {
            // Act & Assert
            assertThatThrownBy(() -> router.getGateway("paypal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not enabled");
        }
    }

    @Nested
    @DisplayName("getDefaultGateway")
    class GetDefaultGateway {

        @Test
        @DisplayName("should return configured default gateway")
        void shouldReturnConfiguredDefaultGateway() {
            // Act
            PaymentGateway gateway = router.getDefaultGateway();

            // Assert
            assertThat(gateway).isEqualTo(mockGateway);
            assertThat(gateway.getName()).isEqualTo("mock");
        }

        @Test
        @DisplayName("should return different default when configured")
        void shouldReturnDifferentDefaultWhenConfigured() {
            // Arrange
            ReflectionTestUtils.setField(router, "defaultGatewayName", "stripe");

            // Act
            PaymentGateway gateway = router.getDefaultGateway();

            // Assert
            assertThat(gateway).isEqualTo(stripeGateway);
        }
    }

    @Nested
    @DisplayName("getGatewayOrDefault")
    class GetGatewayOrDefault {

        @Test
        @DisplayName("should return specified gateway when provided")
        void shouldReturnSpecifiedGatewayWhenProvided() {
            // Act
            PaymentGateway gateway = router.getGatewayOrDefault("stripe");

            // Assert
            assertThat(gateway).isEqualTo(stripeGateway);
        }

        @Test
        @DisplayName("should return default gateway when name is null")
        void shouldReturnDefaultGatewayWhenNameIsNull() {
            // Act
            PaymentGateway gateway = router.getGatewayOrDefault(null);

            // Assert
            assertThat(gateway).isEqualTo(mockGateway);
        }

        @Test
        @DisplayName("should return default gateway when name is empty")
        void shouldReturnDefaultGatewayWhenNameIsEmpty() {
            // Act
            PaymentGateway gateway = router.getGatewayOrDefault("");

            // Assert
            assertThat(gateway).isEqualTo(mockGateway);
        }
    }

    @Nested
    @DisplayName("isGatewayAvailable")
    class IsGatewayAvailable {

        @Test
        @DisplayName("should return true for enabled gateway")
        void shouldReturnTrueForEnabledGateway() {
            // Assert
            assertThat(router.isGatewayAvailable("mock")).isTrue();
            assertThat(router.isGatewayAvailable("stripe")).isTrue();
        }

        @Test
        @DisplayName("should return false for disabled gateway")
        void shouldReturnFalseForDisabledGateway() {
            // Assert
            assertThat(router.isGatewayAvailable("paypal")).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown gateway")
        void shouldReturnFalseForUnknownGateway() {
            // Assert
            assertThat(router.isGatewayAvailable("unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("getEnabledGateways")
    class GetEnabledGateways {

        @Test
        @DisplayName("should return only enabled gateways")
        void shouldReturnOnlyEnabledGateways() {
            // Act
            List<PaymentGateway> enabledGateways = router.getEnabledGateways();

            // Assert
            assertThat(enabledGateways).hasSize(2);
            assertThat(enabledGateways).contains(mockGateway, stripeGateway);
            assertThat(enabledGateways).doesNotContain(paypalGateway);
        }

        @Test
        @DisplayName("should return empty list when all gateways disabled")
        void shouldReturnEmptyListWhenAllGatewaysDisabled() {
            // Arrange
            when(mockGateway.isEnabled()).thenReturn(false);
            when(stripeGateway.isEnabled()).thenReturn(false);

            // Act
            List<PaymentGateway> enabledGateways = router.getEnabledGateways();

            // Assert
            assertThat(enabledGateways).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle case-sensitive gateway names")
        void shouldHandleCaseSensitiveGatewayNames() {
            // Act & Assert - Gateway names are case-sensitive
            assertThatThrownBy(() -> router.getGateway("Mock"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> router.getGateway("STRIPE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should handle gateway becoming disabled after init")
        void shouldHandleGatewayBecomingDisabledAfterInit() {
            // Arrange
            when(stripeGateway.isEnabled()).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> router.getGateway("stripe"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not enabled");
        }
    }
}

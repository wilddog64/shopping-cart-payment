package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
)

type Config struct {
	ServerPort int

	DBHost     string
	DBPort     int
	DBName     string
	DBUsername string
	DBPassword string

	RabbitMQHost         string
	RabbitMQPort         int
	RabbitMQVHost        string
	RabbitMQUsername     string
	RabbitMQPassword     string
	RabbitMQVaultEnabled bool

	PaymentGatewayDefault string

	StripeEnabled       bool
	StripeAPIKey        string
	StripeWebhookSecret string

	PayPalEnabled      bool
	PayPalClientID     string
	PayPalClientSecret string
	PayPalMode         string

	MockGatewayDelayMS     int
	MockGatewayFailureRate float64

	EncryptionEnabled bool
	EncryptionKey     string

	RateLimitEnabled   bool
	RateLimitPerMinute int
	RateLimitPerSecond int
	RateLimitBurst     int

	OAuth2Enabled   bool
	OAuth2IssuerURI string
	OAuth2JWKSetURI string

	LogLevel string
}

func Load() Config {
	return Config{
		ServerPort: intEnv("SERVER_PORT", 8084),

		DBHost:     stringEnv("DB_HOST", "localhost"),
		DBPort:     intEnv("DB_PORT", 5432),
		DBName:     stringEnv("DB_NAME", "payments"),
		DBUsername: stringEnv("DB_USERNAME", "postgres"),
		DBPassword: stringEnv("DB_PASSWORD", "changeme123"),

		RabbitMQHost:         stringEnv("RABBITMQ_HOST", "rabbitmq.shopping-cart-data.svc.cluster.local"),
		RabbitMQPort:         intEnv("RABBITMQ_PORT", 5672),
		RabbitMQVHost:        stringEnv("RABBITMQ_VHOST", "/"),
		RabbitMQUsername:     stringEnv("RABBITMQ_USERNAME", "guest"),
		RabbitMQPassword:     stringEnv("RABBITMQ_PASSWORD", "guest"),
		RabbitMQVaultEnabled: boolEnv("RABBITMQ_VAULT_ENABLED", false),

		PaymentGatewayDefault: stringEnv("PAYMENT_GATEWAY_DEFAULT", "mock"),

		StripeEnabled:       boolEnv("STRIPE_ENABLED", true),
		StripeAPIKey:        stringEnv("STRIPE_API_KEY", "sk_test_xxx"),
		StripeWebhookSecret: stringEnv("STRIPE_WEBHOOK_SECRET", "whsec_xxx"),

		PayPalEnabled:      boolEnv("PAYPAL_ENABLED", true),
		PayPalClientID:     stringEnv("PAYPAL_CLIENT_ID", "xxx"),
		PayPalClientSecret: stringEnv("PAYPAL_CLIENT_SECRET", "xxx"),
		PayPalMode:         stringEnv("PAYPAL_MODE", "sandbox"),

		MockGatewayDelayMS:     intEnv("MOCK_GATEWAY_DELAY_MS", 500),
		MockGatewayFailureRate: floatEnv("MOCK_GATEWAY_FAILURE_RATE", 0.0),

		EncryptionEnabled: boolEnv("ENCRYPTION_ENABLED", true),
		EncryptionKey:     stringEnv("ENCRYPTION_KEY", ""),

		RateLimitEnabled:   boolEnv("RATE_LIMIT_ENABLED", true),
		RateLimitPerMinute: intEnv("RATE_LIMIT_PER_MINUTE", 60),
		RateLimitPerSecond: intEnv("RATE_LIMIT_PER_SECOND", 10),
		RateLimitBurst:     intEnv("RATE_LIMIT_BURST", 20),

		OAuth2Enabled:   boolEnv("OAUTH2_ENABLED", false),
		OAuth2IssuerURI: stringEnv("OAUTH2_ISSUER_URI", "http://keycloak.identity.svc.cluster.local/realms/shopping-cart"),
		OAuth2JWKSetURI: stringEnv("OAUTH2_JWK_SET_URI", "http://keycloak.identity.svc.cluster.local/realms/shopping-cart/protocol/openid-connect/certs"),

		LogLevel: stringEnv("LOG_LEVEL", "INFO"),
	}
}

func (c Config) DatabaseURI() string {
	return fmt.Sprintf("postgres://%s:%s@%s:%d/%s?sslmode=disable",
		url.QueryEscape(c.DBUsername),
		url.QueryEscape(c.DBPassword),
		c.DBHost,
		c.DBPort,
		c.DBName,
	)
}

func stringEnv(name, def string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return def
}

func intEnv(name string, def int) int {
	v := os.Getenv(name)
	if v == "" {
		return def
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return def
	}
	return n
}

func floatEnv(name string, def float64) float64 {
	v := os.Getenv(name)
	if v == "" {
		return def
	}
	n, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return def
	}
	return n
}

func boolEnv(name string, def bool) bool {
	v := os.Getenv(name)
	if v == "" {
		return def
	}
	n, err := strconv.ParseBool(v)
	if err != nil {
		return def
	}
	return n
}

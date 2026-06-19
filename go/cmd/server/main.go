package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"

	"github.com/wilddog64/shopping-cart-payment/go/internal/config"
	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
	"github.com/wilddog64/shopping-cart-payment/go/internal/health"
	"github.com/wilddog64/shopping-cart-payment/go/internal/httpx"
	"github.com/wilddog64/shopping-cart-payment/go/internal/payment"
)

func main() {
	cfg := config.Load()
	logger := newLogger(cfg.LogLevel)
	defer func() { _ = logger.Sync() }()

	gin.SetMode(gin.ReleaseMode)

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	db, err := pgxpool.New(ctx, cfg.DatabaseURI())
	if err != nil {
		logger.Fatal("connect database", zap.Error(err))
	}
	defer db.Close()

	if err := db.Ping(ctx); err != nil {
		logger.Fatal("ping database", zap.Error(err))
	}

	mockGateway := gateway.NewMockGateway(cfg.MockGatewayEnabled, cfg.MockGatewayDelayMS, cfg.MockGatewayFailureRate)
	stripeGateway := gateway.NewStripeGateway(cfg.StripeEnabled && cfg.StripeAPIKey != "")
	paypalGateway := gateway.NewPayPalGateway(cfg.PayPalEnabled && cfg.PayPalClientID != "" && cfg.PayPalClientSecret != "")
	router := gateway.NewRouter(cfg.PaymentGatewayDefault, mockGateway, stripeGateway, paypalGateway)

	store := payment.NewStore(db)
	paymentService := payment.NewPaymentService(store, router)
	refundService := payment.NewRefundService(store, router)
	handler := payment.NewHandler(paymentService, refundService)
	healthHandler := health.New(db)

	engine := gin.New()
	engine.Use(gin.Recovery())
	engine.Use(httpx.SecurityHeaders())
	engine.Use(httpx.RequestLogger(logger))
	engine.Use(httpx.NewRateLimiter(cfg.RateLimitEnabled, cfg.RateLimitPerSecond, cfg.RateLimitBurst))

	healthHandler.Register(engine)
	engine.GET("/actuator/prometheus", gin.WrapH(promhttp.Handler()))
	handler.Register(engine)

	server := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.ServerPort),
		Handler:           engine,
		ReadHeaderTimeout: 10 * time.Second,
	}

	errCh := make(chan error, 1)
	go func() {
		logger.Info("starting payment service", zap.Int("port", cfg.ServerPort))
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			errCh <- err
		}
		close(errCh)
	}()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	select {
	case sig := <-sigCh:
		logger.Info("shutdown requested", zap.String("signal", sig.String()))
	case err := <-errCh:
		if err != nil {
			logger.Fatal("server error", zap.Error(err))
		}
	}

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer shutdownCancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", zap.Error(err))
	}
}

func newLogger(level string) *zap.Logger {
	cfg := zap.NewProductionConfig()
	parsed, err := zapcore.ParseLevel(strings.ToLower(level))
	if err != nil {
		parsed = zapcore.InfoLevel
	}
	cfg.Level = zap.NewAtomicLevelAt(parsed)
	logger, buildErr := cfg.Build()
	if buildErr != nil {
		panic(buildErr)
	}
	return logger
}

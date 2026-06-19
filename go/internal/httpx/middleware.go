package httpx

import (
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"golang.org/x/time/rate"
)

const (
	securityHeaderXSSProtection = "X-XSS-Protection"
	securityHeaderFrameOptions  = "X-Frame-Options"
	securityHeaderContentType   = "X-Content-Type-Options"
	securityHeaderCSP           = "Content-Security-Policy"
	securityHeaderReferrer      = "Referrer-Policy"
	securityHeaderHSTS          = "Strict-Transport-Security"
	securityHeaderPermissions   = "Permissions-Policy"
)

func SecurityHeaders() gin.HandlerFunc {
	return func(c *gin.Context) {
		h := c.Writer.Header()
		h.Set(securityHeaderXSSProtection, "1; mode=block")
		h.Set(securityHeaderFrameOptions, "DENY")
		h.Set(securityHeaderContentType, "nosniff")
		h.Set(securityHeaderCSP, "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; frame-ancestors 'none'; form-action 'self'")
		h.Set(securityHeaderReferrer, "strict-origin-when-cross-origin")
		h.Set(securityHeaderHSTS, "max-age=31536000; includeSubDomains")
		h.Set(securityHeaderPermissions, "geolocation=(), microphone=(), camera=()")
		c.Next()
	}
}

const (
	rateLimiterEntryTTL   = 10 * time.Minute
	rateLimiterSweepEvery = 1 * time.Minute
)

type limiterEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

type limiter struct {
	mu       sync.Mutex
	enabled  bool
	limiters map[string]*limiterEntry
	limit    rate.Limit
	burst    int
}

func NewRateLimiter(enabled bool, perSecond, burst int) gin.HandlerFunc {
	l := &limiter{
		enabled:  enabled,
		limiters: map[string]*limiterEntry{},
		limit:    rate.Limit(perSecond),
		burst:    burst,
	}
	if enabled {
		go l.sweep()
	}
	return func(c *gin.Context) {
		if !l.enabled || strings.HasPrefix(c.FullPath(), "/actuator/") {
			c.Next()
			return
		}
		ip := c.ClientIP()
		rl := l.getLimiter(ip)
		if !rl.Allow() {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"code":    "RATE_LIMIT_EXCEEDED",
				"message": fmt.Sprintf("rate limit exceeded for %s", ip),
			})
			return
		}
		c.Next()
	}
}

func (l *limiter) getLimiter(ip string) *rate.Limiter {
	l.mu.Lock()
	defer l.mu.Unlock()
	entry, ok := l.limiters[ip]
	if !ok {
		entry = &limiterEntry{limiter: rate.NewLimiter(l.limit, l.burst)}
		l.limiters[ip] = entry
	}
	entry.lastSeen = time.Now()
	return entry.limiter
}

func (l *limiter) sweep() {
	ticker := time.NewTicker(rateLimiterSweepEvery)
	defer ticker.Stop()
	for range ticker.C {
		cutoff := time.Now().Add(-rateLimiterEntryTTL)
		l.mu.Lock()
		for ip, entry := range l.limiters {
			if entry.lastSeen.Before(cutoff) {
				delete(l.limiters, ip)
			}
		}
		l.mu.Unlock()
	}
}

func RequestLogger(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		fields := []zap.Field{
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.Int("status", c.Writer.Status()),
			zap.Duration("duration", time.Since(start)),
			zap.String("client_ip", c.ClientIP()),
		}
		if len(c.Errors) > 0 {
			fields = append(fields, zap.String("error", c.Errors.String()))
		}
		logger.Info("request", fields...)
	}
}

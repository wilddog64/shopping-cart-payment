package health

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Handler struct {
	DB *pgxpool.Pool
}

func New(db *pgxpool.Pool) *Handler {
	return &Handler{DB: db}
}

func (h *Handler) Register(r gin.IRoutes) {
	r.GET("/actuator/health", h.ok)
	r.GET("/actuator/health/liveness", h.ok)
	r.GET("/actuator/health/readiness", h.readiness)
	r.GET("/actuator/info", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"app": "payment-service"})
	})
}

func (h *Handler) ok(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "UP"})
}

func (h *Handler) readiness(c *gin.Context) {
	if h.DB == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"status": "DOWN"})
		return
	}
	if err := h.DB.Ping(c.Request.Context()); err != nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"status": "DOWN"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"status": "UP"})
}

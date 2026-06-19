package gateway

import (
	"fmt"
	"strings"
)

type PaymentGateway interface {
	GetName() string
	IsEnabled() bool
	ProcessPayment(request PaymentRequest) PaymentResult
	ProcessRefund(request RefundRequest) RefundResult
	Tokenize(request TokenizeRequest) TokenizeResult
	DeleteToken(token string) bool
	SupportsRecurring() bool
	SupportsPartialRefund() bool
}

type Router struct {
	gateways           map[string]PaymentGateway
	defaultGatewayName string
}

func NewRouter(defaultGatewayName string, gateways ...PaymentGateway) *Router {
	router := &Router{
		gateways:           make(map[string]PaymentGateway),
		defaultGatewayName: defaultGatewayName,
	}
	for _, gateway := range gateways {
		if gateway == nil {
			continue
		}
		router.gateways[strings.ToLower(gateway.GetName())] = gateway
	}
	return router
}

func (r *Router) GetGateway(name string) (PaymentGateway, error) {
	gateway, ok := r.gateways[strings.ToLower(name)]
	if !ok {
		return nil, fmt.Errorf("unknown gateway: %s", name)
	}
	if !gateway.IsEnabled() {
		return nil, fmt.Errorf("gateway is not enabled: %s", name)
	}
	return gateway, nil
}

func (r *Router) GetDefaultGateway() (PaymentGateway, error) {
	return r.GetGateway(r.defaultGatewayName)
}

func (r *Router) GetGatewayOrDefault(name string) (PaymentGateway, error) {
	if strings.TrimSpace(name) == "" {
		return r.GetDefaultGateway()
	}
	return r.GetGateway(name)
}

func (r *Router) IsGatewayAvailable(name string) bool {
	gateway, ok := r.gateways[strings.ToLower(name)]
	return ok && gateway.IsEnabled()
}

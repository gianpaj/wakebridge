package probe

import (
	"context"
	"errors"
	"net"
	"syscall"
	"time"
)

// TCP checks only the configured port; it does not authenticate or run commands.
type TCP struct{ address string }

func New(address string) *TCP { return &TCP{address: address} }

func (p *TCP) Online(ctx context.Context) (bool, error) {
	dialer := net.Dialer{Timeout: time.Second}
	conn, err := dialer.DialContext(ctx, "tcp", p.address)
	if err == nil {
		_ = conn.Close()
		return true, nil
	}
	if ctx.Err() != nil {
		return false, ctx.Err()
	}
	// A DNS/configuration failure must not be reported as a sleeping machine.
	var dnsErr *net.DNSError
	if errors.As(err, &dnsErr) {
		return false, err
	}
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return false, nil
	}
	if errors.Is(err, syscall.ECONNREFUSED) || errors.Is(err, syscall.EHOSTUNREACH) || errors.Is(err, syscall.ENETUNREACH) {
		return false, nil
	}
	return false, err
}

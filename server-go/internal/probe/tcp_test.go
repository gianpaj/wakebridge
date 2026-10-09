package probe

import (
	"context"
	"errors"
	"io"
	"net"
	"testing"
	"time"
)

func TestOnlineConnectsAndCloses(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	closed := make(chan error, 1)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			closed <- err
			return
		}
		defer conn.Close()
		_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
		var b [1]byte
		_, err = conn.Read(b[:])
		closed <- err
	}()
	online, err := New(listener.Addr().String()).Online(context.Background())
	if err != nil || !online {
		t.Fatalf("Online() = %v, %v", online, err)
	}
	if err := <-closed; !errors.Is(err, io.EOF) {
		t.Fatalf("connection not closed: %v", err)
	}
}
func TestClosedPortIsNotOnline(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := listener.Addr().String()
	listener.Close()
	online, err := New(address).Online(context.Background())
	if err != nil || online {
		t.Fatalf("Online() = %v, %v", online, err)
	}
}
func TestCancelledProbe(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	online, err := New("127.0.0.1:22").Online(ctx)
	if online || !errors.Is(err, context.Canceled) {
		t.Fatalf("Online() = %v, %v", online, err)
	}
}
func TestInvalidAddressIsAnError(t *testing.T) {
	online, err := New("missing-port").Online(context.Background())
	if online || err == nil {
		t.Fatalf("Online() = %v, %v", online, err)
	}
}

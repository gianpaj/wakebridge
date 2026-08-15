package config

import (
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
)

const (
	defaultListenAddr = "127.0.0.1:8787"
	defaultWOLPort    = 9
)

type Config struct {
	ListenAddr string
	APIToken   string
	MAC        net.HardwareAddr
	Broadcast  net.IP
	WOLPort    int
}

func Load(getenv func(string) string) (Config, error) {
	token := getenv("WAKE_API_TOKEN")
	if token == "" {
		return Config{}, errors.New("WAKE_API_TOKEN is required")
	}

	macText := getenv("GIANRTX_MAC")
	if macText == "" {
		return Config{}, errors.New("GIANRTX_MAC is required")
	}
	mac, err := net.ParseMAC(macText)
	if err != nil || len(mac) != 6 {
		return Config{}, errors.New("GIANRTX_MAC must be a 6-byte MAC address")
	}

	broadcastText := getenv("WOL_BROADCAST")
	if broadcastText == "" {
		return Config{}, errors.New("WOL_BROADCAST is required")
	}
	broadcast := net.ParseIP(broadcastText)
	if broadcast == nil || broadcast.To4() == nil {
		return Config{}, errors.New("WOL_BROADCAST must be an IPv4 address")
	}

	listenAddr := getenv("LISTEN_ADDR")
	if listenAddr == "" {
		listenAddr = defaultListenAddr
	}
	if err := validateListenAddr(listenAddr); err != nil {
		return Config{}, fmt.Errorf("LISTEN_ADDR: %w", err)
	}

	wolPort := defaultWOLPort
	if portText := getenv("WOL_PORT"); portText != "" {
		port, err := strconv.Atoi(portText)
		if err != nil || port < 1 || port > 65535 {
			return Config{}, errors.New("WOL_PORT must be an integer from 1 to 65535")
		}
		wolPort = port
	}

	return Config{
		ListenAddr: listenAddr,
		APIToken:   token,
		MAC:        append(net.HardwareAddr(nil), mac...),
		Broadcast:  append(net.IP(nil), broadcast.To4()...),
		WOLPort:    wolPort,
	}, nil
}

func validateListenAddr(address string) error {
	host, portText, err := net.SplitHostPort(address)
	if err != nil {
		return errors.New("must use host:port form")
	}
	if strings.TrimSpace(host) == "" {
		return errors.New("host must not be empty")
	}
	port, err := strconv.Atoi(portText)
	if err != nil || port < 1 || port > 65535 {
		return errors.New("port must be an integer from 1 to 65535")
	}
	return nil
}

package config

import (
	"net"
	"strings"
	"testing"
)

func TestLoadValidConfiguration(t *testing.T) {
	values := map[string]string{
		"WAKE_API_TOKEN": "secret",
		"GIANRTX_MAC":    "00:11:22:33:44:55",
		"WOL_BROADCAST":  "192.168.1.255",
	}

	cfg, err := Load(mapGetter(values))
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.ListenAddr != "127.0.0.1:8787" {
		t.Fatalf("ListenAddr = %q", cfg.ListenAddr)
	}
	if cfg.WOLPort != 9 {
		t.Fatalf("WOLPort = %d", cfg.WOLPort)
	}
	if got := cfg.MAC.String(); got != "00:11:22:33:44:55" {
		t.Fatalf("MAC = %q", got)
	}
	if !cfg.Broadcast.Equal(net.ParseIP("192.168.1.255")) {
		t.Fatalf("Broadcast = %v", cfg.Broadcast)
	}
}

func TestLoadRejectsInvalidMAC(t *testing.T) {
	values := map[string]string{
		"WAKE_API_TOKEN": "secret",
		"GIANRTX_MAC":    "not-a-mac",
		"WOL_BROADCAST":  "192.168.1.255",
	}

	_, err := Load(mapGetter(values))
	if err == nil || !strings.Contains(err.Error(), "GIANRTX_MAC") {
		t.Fatalf("Load() error = %v", err)
	}
}

func TestLoadRejectsMissingAndMalformedValues(t *testing.T) {
	tests := []struct {
		name   string
		values map[string]string
		want   string
	}{
		{
			name: "missing token",
			values: map[string]string{
				"GIANRTX_MAC":   "00:11:22:33:44:55",
				"WOL_BROADCAST": "192.168.1.255",
			},
			want: "WAKE_API_TOKEN",
		},
		{
			name: "invalid broadcast",
			values: map[string]string{
				"WAKE_API_TOKEN": "secret",
				"GIANRTX_MAC":    "00:11:22:33:44:55",
				"WOL_BROADCAST":  "example.com",
			},
			want: "WOL_BROADCAST",
		},
		{
			name: "invalid port",
			values: map[string]string{
				"WAKE_API_TOKEN": "secret",
				"GIANRTX_MAC":    "00:11:22:33:44:55",
				"WOL_BROADCAST":  "192.168.1.255",
				"WOL_PORT":       "70000",
			},
			want: "WOL_PORT",
		},
		{
			name: "invalid listen address",
			values: map[string]string{
				"WAKE_API_TOKEN": "secret",
				"GIANRTX_MAC":    "00:11:22:33:44:55",
				"WOL_BROADCAST":  "192.168.1.255",
				"LISTEN_ADDR":    "localhost",
			},
			want: "LISTEN_ADDR",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := Load(mapGetter(tt.values))
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("Load() error = %v, want text %q", err, tt.want)
			}
		})
	}
}

func mapGetter(values map[string]string) func(string) string {
	return func(key string) string { return values[key] }
}

func TestSSHAddress(t *testing.T) {
	for _, address := range []string{"", "192.168.1.100:22", "gianRTX:22", "[::1]:22", "missing-port", ":22", "host:0", "host:65536", "bad host:22"} {
		t.Run(address, func(t *testing.T) {
			values := map[string]string{"WAKE_API_TOKEN": "secret", "GIANRTX_MAC": "00:11:22:33:44:55", "WOL_BROADCAST": "192.168.1.255", "GIANRTX_SSH_ADDR": address}
			cfg, err := Load(mapGetter(values))
			valid := address == "" || address == "192.168.1.100:22" || address == "gianRTX:22" || address == "[::1]:22"
			if valid {
				if err != nil || cfg.SSHAddr != address {
					t.Fatalf("Load() = %+v, %v", cfg, err)
				}
			} else if err == nil || !strings.Contains(err.Error(), "GIANRTX_SSH_ADDR") {
				t.Fatalf("error = %v", err)
			}
		})
	}
}

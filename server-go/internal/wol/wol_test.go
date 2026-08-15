package wol

import (
	"bytes"
	"net"
	"testing"
)

func TestMagicPacketContents(t *testing.T) {
	mac := net.HardwareAddr{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}

	packet, err := MagicPacket(mac)
	if err != nil {
		t.Fatalf("MagicPacket() error = %v", err)
	}
	if len(packet) != 102 {
		t.Fatalf("packet length = %d", len(packet))
	}
	if !bytes.Equal(packet[:6], bytes.Repeat([]byte{0xff}, 6)) {
		t.Fatalf("packet prefix = %x", packet[:6])
	}
	for offset := 6; offset < len(packet); offset += len(mac) {
		if !bytes.Equal(packet[offset:offset+len(mac)], mac) {
			t.Fatalf("MAC at offset %d = %x", offset, packet[offset:offset+len(mac)])
		}
	}
}

func TestMagicPacketRejectsInvalidMAC(t *testing.T) {
	if _, err := MagicPacket(net.HardwareAddr{0x00, 0x11}); err == nil {
		t.Fatal("MagicPacket() accepted an invalid MAC")
	}
}

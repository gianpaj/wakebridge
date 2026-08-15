package wol

import (
	"context"
	"errors"
	"fmt"
	"net"
	"time"
)

const (
	magicPacketSize = 6 + 16*6
	defaultRepeats  = 3
)

type Sender struct {
	mac         net.HardwareAddr
	destination *net.UDPAddr
	repeats     int
}

func NewSender(mac net.HardwareAddr, broadcast net.IP, port int) (*Sender, error) {
	if len(mac) != 6 {
		return nil, errors.New("MAC address must contain 6 bytes")
	}
	if broadcast.To4() == nil {
		return nil, errors.New("broadcast address must be IPv4")
	}
	if port < 1 || port > 65535 {
		return nil, errors.New("port must be from 1 to 65535")
	}

	return &Sender{
		mac: append(net.HardwareAddr(nil), mac...),
		destination: &net.UDPAddr{
			IP:   append(net.IP(nil), broadcast.To4()...),
			Port: port,
		},
		repeats: defaultRepeats,
	}, nil
}

func MagicPacket(mac net.HardwareAddr) ([]byte, error) {
	if len(mac) != 6 {
		return nil, errors.New("MAC address must contain 6 bytes")
	}

	packet := make([]byte, magicPacketSize)
	for i := 0; i < 6; i++ {
		packet[i] = 0xff
	}
	for offset := 6; offset < len(packet); offset += len(mac) {
		copy(packet[offset:], mac)
	}
	return packet, nil
}

func (s *Sender) Wake(ctx context.Context) error {
	packet, err := MagicPacket(s.mac)
	if err != nil {
		return err
	}

	conn, err := net.ListenUDP("udp4", nil)
	if err != nil {
		return fmt.Errorf("open UDP socket: %w", err)
	}
	defer conn.Close()

	if err := enableBroadcast(conn); err != nil {
		return fmt.Errorf("enable UDP broadcast: %w", err)
	}
	if err := conn.SetWriteDeadline(time.Now().Add(2 * time.Second)); err != nil {
		return fmt.Errorf("set UDP deadline: %w", err)
	}

	for i := 0; i < s.repeats; i++ {
		if err := ctx.Err(); err != nil {
			return err
		}
		written, err := conn.WriteToUDP(packet, s.destination)
		if err != nil {
			return fmt.Errorf("send magic packet: %w", err)
		}
		if written != len(packet) {
			return fmt.Errorf("send magic packet: wrote %d of %d bytes", written, len(packet))
		}
	}
	return nil
}

package httpapi

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

type Waker interface {
	Wake(context.Context) error
}

type Handler struct {
	tokenHash [sha256.Size]byte
	waker     Waker
	logger    *slog.Logger
}

func NewHandler(token string, waker Waker, logger *slog.Logger) *Handler {
	return &Handler{
		tokenHash: sha256.Sum256([]byte(token)),
		waker:     waker,
		logger:    logger,
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/health":
		h.handleHealth(w, r)
	case "/wake":
		h.handleWake(w, r)
	default:
		start := time.Now()
		writeJSON(w, http.StatusNotFound, map[string]any{"ok": false, "error": "not found"})
		h.logger.Info("unknown route",
			"path", r.URL.Path,
			"status", http.StatusNotFound,
			"duration_ms", time.Since(start).Milliseconds(),
		)
	}
}

func (h *Handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	if r.Method != http.MethodGet {
		w.Header().Set("Allow", http.MethodGet)
		writeJSON(w, http.StatusMethodNotAllowed, map[string]any{"ok": false, "error": "method not allowed"})
		h.logRequest("health request", r.URL.Path, http.StatusMethodNotAllowed, start)
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
	h.logRequest("health request", r.URL.Path, http.StatusOK, start)
}

func (h *Handler) handleWake(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		writeJSON(w, http.StatusMethodNotAllowed, map[string]any{"ok": false, "error": "method not allowed"})
		h.logRequest("wake request received", r.URL.Path, http.StatusMethodNotAllowed, start)
		return
	}

	h.logger.Info("wake request received", "path", r.URL.Path)
	if !h.authorized(r.Header.Get("Authorization")) {
		writeJSON(w, http.StatusUnauthorized, map[string]any{"ok": false, "error": "unauthorized"})
		h.logRequest("unauthorized request", r.URL.Path, http.StatusUnauthorized, start)
		return
	}

	if err := h.waker.Wake(r.Context()); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": "wake failed"})
		h.logger.Error("wake failed",
			"path", r.URL.Path,
			"status", http.StatusInternalServerError,
			"duration_ms", time.Since(start).Milliseconds(),
			"error", err,
		)
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "target": "gianRTX"})
	h.logRequest("wake successful", r.URL.Path, http.StatusOK, start)
}

func (h *Handler) authorized(header string) bool {
	const prefix = "Bearer "
	validFormat := 0
	provided := ""
	if strings.HasPrefix(header, prefix) {
		validFormat = 1
		provided = header[len(prefix):]
	}
	providedHash := sha256.Sum256([]byte(provided))
	return validFormat&subtle.ConstantTimeCompare(providedHash[:], h.tokenHash[:]) == 1
}

func (h *Handler) logRequest(message, path string, status int, start time.Time) {
	h.logger.Info(message,
		"path", path,
		"status", status,
		"duration_ms", time.Since(start).Milliseconds(),
	)
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

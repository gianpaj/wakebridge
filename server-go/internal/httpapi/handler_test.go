package httpapi

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type fakeWaker struct {
	calls int
	err   error
}

func (f *fakeWaker) Wake(context.Context) error {
	f.calls++
	return f.err
}

func TestHandlerRoutesAndAuthentication(t *testing.T) {
	tests := []struct {
		name      string
		method    string
		path      string
		token     string
		wantCode  int
		wantBody  string
		wantWakes int
	}{
		{name: "health", method: http.MethodGet, path: "/health", wantCode: 200, wantBody: `"ok":true`},
		{name: "valid bearer", method: http.MethodPost, path: "/wake", token: "secret", wantCode: 200, wantBody: `"target":"gianRTX"`, wantWakes: 1},
		{name: "invalid bearer", method: http.MethodPost, path: "/wake", token: "wrong", wantCode: 401, wantBody: `"error":"unauthorized"`},
		{name: "missing bearer", method: http.MethodPost, path: "/wake", wantCode: 401, wantBody: `"error":"unauthorized"`},
		{name: "unknown route", method: http.MethodGet, path: "/other", wantCode: 404, wantBody: `"error":"not found"`},
		{name: "wrong health method", method: http.MethodPost, path: "/health", wantCode: 405, wantBody: `"error":"method not allowed"`},
		{name: "wrong wake method", method: http.MethodGet, path: "/wake", wantCode: 405, wantBody: `"error":"method not allowed"`},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			waker := &fakeWaker{}
			handler := NewHandler("secret", waker, discardLogger())
			request := httptest.NewRequest(tt.method, tt.path, nil)
			if tt.token != "" {
				request.Header.Set("Authorization", "Bearer "+tt.token)
			}
			response := httptest.NewRecorder()

			handler.ServeHTTP(response, request)

			if response.Code != tt.wantCode {
				t.Fatalf("status = %d, want %d", response.Code, tt.wantCode)
			}
			if !strings.Contains(response.Body.String(), tt.wantBody) {
				t.Fatalf("body = %q, want text %q", response.Body.String(), tt.wantBody)
			}
			if waker.calls != tt.wantWakes {
				t.Fatalf("wake calls = %d, want %d", waker.calls, tt.wantWakes)
			}
		})
	}
}

func TestHandlerReturnsServerErrorWhenWakeFails(t *testing.T) {
	waker := &fakeWaker{err: errors.New("UDP unavailable")}
	handler := NewHandler("secret", waker, discardLogger())
	request := httptest.NewRequest(http.MethodPost, "/wake", nil)
	request.Header.Set("Authorization", "Bearer secret")
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)

	if response.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d", response.Code)
	}
	if waker.calls != 1 {
		t.Fatalf("wake calls = %d", waker.calls)
	}
}

func discardLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

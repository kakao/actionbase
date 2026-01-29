package util

import (
	"bytes"
	"context"
	"log/slog"
	"strings"
	"testing"
	"time"
)

func TestNewLogger(t *testing.T) {
	logger := NewLogger(slog.LevelInfo)
	if logger == nil {
		t.Error("NewLogger returned nil")
	}
}

func TestSimpleHandler_Enabled(t *testing.T) {
	handler := NewSimpleHandler(nil, slog.LevelInfo)
	if !handler.Enabled(context.Background(), slog.LevelInfo) {
		t.Error("LevelInfo should be enabled for LevelInfo")
	}
	if !handler.Enabled(context.Background(), slog.LevelError) {
		t.Error("LevelError should be enabled for LevelInfo")
	}
	if handler.Enabled(context.Background(), slog.LevelDebug) {
		t.Error("LevelDebug should not be enabled for LevelInfo")
	}
}

func TestSimpleHandler_Handle(t *testing.T) {
	var buf bytes.Buffer
	handler := NewSimpleHandler(&buf, slog.LevelInfo)

	record := slog.NewRecord(time.Now(), slog.LevelInfo, "test message", 0)
	err := handler.Handle(context.Background(), record)
	if err != nil {
		t.Errorf("Handle failed: %v", err)
	}

	output := buf.String()
	if !strings.Contains(output, "test message") {
		t.Errorf("Output does not contain message: %q", output)
	}
	if !strings.Contains(output, "\033[90m") {
		t.Errorf("Output does not contain gray color: %q", output)
	}
}

func TestSimpleHandler_WithAttrs(t *testing.T) {
	handler := NewSimpleHandler(nil, slog.LevelInfo)
	newHandler := handler.WithAttrs([]slog.Attr{slog.String("key", "value")})
	if newHandler != handler {
		t.Error("WithAttrs should return the same handler")
	}
}

func TestSimpleHandler_WithGroup(t *testing.T) {
	handler := NewSimpleHandler(nil, slog.LevelInfo)
	newHandler := handler.WithGroup("group")
	if newHandler != handler {
		t.Error("WithGroup should return the same handler")
	}
}
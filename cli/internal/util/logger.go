package util

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"
)

func NewLogger(level slog.Level) *slog.Logger {
	handler := NewSimpleHandler(os.Stdout, level)
	return slog.New(handler)
}

type SimpleHandler struct {
	out   io.Writer
	level slog.Level
}

func NewSimpleHandler(out io.Writer, level slog.Level) *SimpleHandler {
	return &SimpleHandler{out: out, level: level}
}

func (h *SimpleHandler) Enabled(_ context.Context, l slog.Level) bool {
	return l >= h.level
}

func (h *SimpleHandler) Handle(_ context.Context, r slog.Record) error {
	message := r.Message
	_, err := fmt.Fprintf(h.out, "\033[90m  \u2502 %s\033[0m\n", message)
	return err
}

func (h *SimpleHandler) WithAttrs(_ []slog.Attr) slog.Handler {
	return h
}

func (h *SimpleHandler) WithGroup(_ string) slog.Handler {
	return h
}

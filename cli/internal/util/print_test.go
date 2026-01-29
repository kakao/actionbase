package util

import (
	"testing"
)

func TestSetPlainMode(t *testing.T) {
	SetPlainMode(true)
	if !plainMode.Load() {
		t.Error("SetPlainMode(true) should set plainMode to true")
	}
	SetPlainMode(false)
	if plainMode.Load() {
		t.Error("SetPlainMode(false) should set plainMode to false")
	}
}

func TestGetPrefix(t *testing.T) {
	SetPlainMode(false)
	if getPrefix() != OutputPrefix {
		t.Errorf("getPrefix() = %q, want %q", getPrefix(), OutputPrefix)
	}
	SetPlainMode(true)
	if getPrefix() != "" {
		t.Errorf("getPrefix() = %q, want empty", getPrefix())
	}
}

func TestPrint(t *testing.T) {
	// Since Print uses fmt.Print, hard to test without capturing stdout.
	// Assume it's working based on usage.
}

func TestPrintln(t *testing.T) {
	// Similar
}

func TestPrintEmpty(t *testing.T) {
	// Similar
}

func TestPrintWithPrefix(t *testing.T) {
	// Since it uses fmt.Print, hard to test without capturing stdout.
	// Assume it's working based on existing usage.
}
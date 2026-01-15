export CGO_ENABLED := 0

.PHONY: build clean run test install fmt vet

BINARY_NAME=actionbase
MAIN_PATH=./cmd/actionbase
BUILD_DIR=./build

SERVICE := cli
VERSION := $(shell git describe --tags --always --match "$(SERVICE)/*" 2>/dev/null | sed 's|$(SERVICE)/||')
GOBASE=$(shell pwd)
GOBIN=$(GOBASE)/bin
GOFILES=$(wildcard *.go)

LD_FLAGS := -s -w -X main.Version=$(VERSION)

all: fmt vet build

build:
	@echo "Building..."
	@mkdir -p $(BUILD_DIR)
	@echo "$(VERSION)"
	@go build -ldflags "$(LD_FLAGS)" -o $(BUILD_DIR)/$(BINARY_NAME) $(MAIN_PATH)
	@echo "Build complete: $(BUILD_DIR)/$(BINARY_NAME)"

install:
	@echo "Installing..."
	@go install $(MAIN_PATH)
	@echo "Installed to $(GOPATH)/bin/$(BINARY_NAME)"

run: build
	@echo "Running..."
	@$(BUILD_DIR)/$(BINARY_NAME)

test:
	@echo "Testing..."
	@go test -v ./...

clean:
	@echo "Cleaning..."
	@rm -rf $(BUILD_DIR)
	@go clean
	@echo "Clean complete"

lint:
	@echo "Formatting..."
	@go fmt ./...

vet:
	@echo "Vetting..."
	@go vet ./...

deps:
	@echo "Downloading dependencies..."
	@go mod download
	@echo "Dependencies downloaded"

tidy:
	@echo "Tidying dependencies..."
	@go mod tidy
	@echo "Dependencies tidied"

build-linux:
	@echo "Building for Linux..."

	@echo "$(BUILD_DIR)"
	@echo "$(BINARY_NAME)"
	@echo "$(VERSION)"
	@CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags "$(LD_FLAGS)" -o $(BUILD_DIR)/$(BINARY_NAME)_$(VERSION)_linux_amd64/$(BINARY_NAME) $(MAIN_PATH)
	@echo "Build complete: $(BUILD_DIR)/$(BINARY_NAME)_linux_amd64"

build-macos:
	@echo "Building for macOS..."
	@echo "CGO_ENABLED=$$CGO_ENABLED"
	@CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -ldflags "$(LD_FLAGS)" -o $(BUILD_DIR)/$(BINARY_NAME)_$(VERSION)_darwin_amd64/${BINARY_NAME} $(MAIN_PATH)
	@CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -ldflags "$(LD_FLAGS)" -o $(BUILD_DIR)/$(BINARY_NAME)_$(VERSION)_darwin_arm64/${BINARY_NAME} $(MAIN_PATH)
	@echo "Build complete: $(BUILD_DIR)/$(BINARY_NAME)_darwin_*"

build-windows:
	@echo "Building for Windows..."
	@CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -ldflags "$(LD_FLAGS)" -o $(BUILD_DIR)/$(BINARY_NAME)_$(VERSION)_windows_amd64/${BINARY_NAME}.exe $(MAIN_PATH)
	@echo "Build complete: $(BUILD_DIR)/$(BINARY_NAME)_windows_amd64.exe"

build-all: build-linux build-macos build-windows
	@echo "All builds complete"

help:
	@echo "Actionbase CLI Makefile"
	@echo ""
	@echo "Usage:"
	@echo "  make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all          - Format, vet, and build (default)"
	@echo "  build        - Build the binary"
	@echo "  install      - Install the binary to GOPATH/bin"
	@echo "  run          - Build and run the binary"
	@echo "  test         - Run tests"
	@echo "  clean        - Remove build artifacts"
	@echo "  fmt          - Format Go code"
	@echo "  vet          - Run go vet"
	@echo "  deps         - Download dependencies"
	@echo "  tidy         - Tidy dependencies"
	@echo "  build-linux  - Build for Linux AMD64"
	@echo "  build-macos  - Build for macOS (AMD64 and ARM64)"
	@echo "  build-windows- Build for Windows AMD64"
	@echo "  build-all    - Build for all platforms"
	@echo "  help         - Show this help message"

# Actionbase Standalone Image
# Usage: docker run -it ghcr.io/kakao/actionbase:standalone
#
# This image bundles the Spring Boot server and Go CLI into a single container.
# The server runs in the background with in-memory storage, while the CLI
# provides an interactive interface in the foreground.

# ============================================================================
# Stage 1: Build server JAR
# ============================================================================
FROM azul/zulu-openjdk:17 AS server-builder

WORKDIR /build

# Install required tools
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# Copy gradle wrapper and build files first for caching
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY conventions/ conventions/
COPY platform/ platform/
COPY .git/ .git/

# Copy source modules
COPY codec-java/ codec-java/
COPY core-java/ core-java/
COPY core/ core/
COPY engine/ engine/
COPY server/ server/

# Enable toolchain auto-provisioning and build server JAR (skip tests for faster build)
ENV GRADLE_OPTS="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"
RUN chmod +x gradlew && \
    ./gradlew :server:bootJar -x test --no-daemon

# ============================================================================
# Stage 2: Build CLI binaries (cross-compile both architectures)
# ============================================================================
FROM --platform=$BUILDPLATFORM golang:1.21-alpine AS cli-builder

WORKDIR /build

# Install build dependencies
RUN apk add --no-cache git

# Copy CLI source
COPY cli/ .

# Cross-compile for both architectures
RUN VERSION=$(git describe --tags --always --match "cli/*" 2>/dev/null | sed 's|cli/||' || echo "standalone") && \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags "-s -w -X main.Version=${VERSION}" \
    -o /actionbase-amd64 ./cmd/actionbase && \
    CGO_ENABLED=0 GOOS=linux GOARCH=arm64 \
    go build -ldflags "-s -w -X main.Version=${VERSION}" \
    -o /actionbase-arm64 ./cmd/actionbase

# ============================================================================
# Stage 3: Runtime image
# ============================================================================
FROM azul/zulu-openjdk-alpine:17-jre

LABEL org.opencontainers.image.title="Actionbase Standalone"
LABEL org.opencontainers.image.description="All-in-one Actionbase container with server and CLI"
LABEL org.opencontainers.image.source="https://github.com/kakao/actionbase"
LABEL org.opencontainers.image.vendor="Kakao"

# Install required tools
RUN apk add --no-cache bash curl

# Create app directory and user
RUN addgroup -g 1000 actionbase && \
    adduser -u 1000 -G actionbase -s /bin/sh -D actionbase

WORKDIR /app

# Copy server JAR
COPY --from=server-builder /build/server/build/libs/server-*-SNAPSHOT.jar /app/server.jar

# Copy CLI binary for target architecture
ARG TARGETARCH
COPY --from=cli-builder /actionbase-${TARGETARCH} /usr/local/bin/actionbase

# Copy entrypoint script
COPY docker/standalone/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Set environment variables
ENV TZ=Asia/Seoul
ENV AB_SERVER_HOST=localhost
ENV AB_SERVER_PORT=8080

# Expose server port
EXPOSE 8080

# Switch to non-root user
USER actionbase

ENTRYPOINT ["/docker-entrypoint.sh"]

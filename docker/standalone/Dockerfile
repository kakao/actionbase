# Actionbase Standalone for Quick Start
#
# docker run -it ghcr.io/kakao/actionbase:standalone

FROM azul/zulu-openjdk-alpine:17-jre

LABEL org.opencontainers.image.title="Actionbase Standalone"
LABEL org.opencontainers.image.description="All-in-one container for quick start"
LABEL org.opencontainers.image.source="https://github.com/kakao/actionbase"

# Install required tools
RUN apk add --no-cache bash curl

# Create app directory and user
RUN addgroup -g 1000 actionbase && \
    adduser -u 1000 -G actionbase -s /bin/sh -D actionbase

WORKDIR /app

# Copy pre-built artifacts from local build
ARG TARGETARCH
COPY server/build/libs/server-*-SNAPSHOT.jar /app/server.jar
COPY docker/standalone/build/actionbase-${TARGETARCH} /usr/local/bin/actionbase

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

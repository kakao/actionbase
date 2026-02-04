# Multi-stage build for optimized image
FROM eclipse-temurin:21-jdk-jammy AS builder

# HBase version
ENV HBASE_VERSION=2.5.13

# Download and prepare HBase
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    wget -q https://dlcdn.apache.org/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz && \
    tar -xzf hbase-${HBASE_VERSION}-bin.tar.gz && \
    mv hbase-${HBASE_VERSION} /opt/hbase && \
    rm hbase-${HBASE_VERSION}-bin.tar.gz && \
    cd /opt/hbase && \
    # Remove unnecessary files (keep lib/ruby for HBase Shell!)
    rm -rf docs src *.txt LEGAL LICENSE NOTICE CHANGES.md && \
    rm -rf bin/*.cmd && \
    find . -name "*-tests.jar" -delete && \
    find . -name "*-test-sources.jar" -delete && \
    find . -name "*.sh.orig" -delete

# Runtime image
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Actionbase Developers"
LABEL version="2.5.13"
LABEL description="HBase Standalone for Development"

# Environment variables
ENV HBASE_VERSION=2.5.13 \
    HBASE_HOME=/opt/hbase \
    PATH=/opt/hbase/bin:$PATH \
    JAVA_HOME=/opt/java/openjdk

# Install minimal required packages
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        procps \
        curl \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    apt-get clean

# Copy HBase from builder
COPY --from=builder /opt/hbase /opt/hbase

# Copy hbase-site.xml into image
COPY conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml

# Create data directories
RUN mkdir -p /data/hbase /data/zookeeper

# Expose ports
EXPOSE 16000 16010 16020 16030 2181 9090 9095

# Startup script (silent - logs go to /opt/hbase/logs/)
COPY <<'EOF' /start-hbase.sh
#!/bin/bash
set -e

exec > /opt/hbase/logs/startup.log 2>&1

# Disable SVE warning (ARM architecture issue)
export HBASE_OPTS="${HBASE_OPTS} -XX:UseSVE=0"
export HBASE_MASTER_OPTS="${HBASE_MASTER_OPTS} -XX:UseSVE=0"
export HBASE_REGIONSERVER_OPTS="${HBASE_REGIONSERVER_OPTS} -XX:UseSVE=0"

# Clean up stale ZooKeeper files
rm -f /data/zookeeper/zookeeper_server.pid 2>/dev/null || true

${HBASE_HOME}/bin/start-hbase.sh

sleep 10

# Create namespace and default table if they don't exist
${HBASE_HOME}/bin/hbase shell <<'HBASE_SHELL'
namespaces = list_namespace.to_a
unless namespaces.include?('ab_local')
  create_namespace 'ab_local'
end
unless exists('ab_local:ab_local_default')
  create 'ab_local:ab_local_default', {
    NAME => 'f',
    BLOOMFILTER => 'ROW',
    IN_MEMORY => 'false',
    KEEP_DELETED_CELLS => 'FALSE',
    DATA_BLOCK_ENCODING => 'FAST_DIFF',
    COMPRESSION => 'GZ',
    TTL => 'FOREVER',
    VERSIONS => 1,
    MIN_VERSIONS => 0,
    BLOCKCACHE => 'true',
    BLOCKSIZE => 65536
  }
end
exit
HBASE_SHELL

# Keep container running
tail -f /dev/null
EOF

RUN chmod +x /start-hbase.sh

WORKDIR ${HBASE_HOME}

CMD ["/start-hbase.sh"]

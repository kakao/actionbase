# Public-friendly HBase standalone image for integration tests.
# Base: eclipse-temurin:8-jre-jammy (public JDK 8).
# HBase binary from Apache archive/dlcdn mirror.
#
# HBase version 2.5.14 is 2.5.x minor-compatible with the pipeline's
# hbase-shaded-client 2.5.10 artifact (2.5.10 itself is only on the
# throttled archive.apache.org mirror, so dlcdn serves 2.5.14 quickly).
#
# Target user: testcontainers-java building the image once from the test
# classpath (see `HBaseContainer.image`).

FROM eclipse-temurin:8-jre-jammy

USER root

ENV HBASE_HOME=/usr/local/hbase
ARG HBASE_VERSION=2.5.14
ENV HBASE_ZOOKEEPER_QUORUM=localhost

# bash is already present on the jammy base; curl + tar + gzip are needed
# to fetch and unpack the Apache HBase archive.
RUN apt-get update \
 && apt-get install -y --no-install-recommends bash curl tar gzip \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://dlcdn.apache.org/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz" \
      -o /tmp/hbase.tar.gz \
 && tar -xzf /tmp/hbase.tar.gz -C /usr/local/ \
 && mv "/usr/local/hbase-${HBASE_VERSION}" "${HBASE_HOME}" \
 && rm /tmp/hbase.tar.gz

ADD hbase-site.xml ${HBASE_HOME}/conf/hbase-site.xml

# zookeeper
EXPOSE 2181
# HBase Master API port
EXPOSE 16000
# HBase Master Web UI
EXPOSE 16010
# Regionserver API port
EXPOSE 16020
# HBase Regionserver web UI
EXPOSE 16030

WORKDIR /usr/local/hbase

# Match the kc-deploy reference: start HBase in standalone mode and tail the
# master log so the container stays alive and testcontainers can wait on the
# expected log line.
CMD ["/bin/bash", "-c", "cd bin; ./start-hbase.sh; tail -f $HBASE_HOME/logs/hbase-$USER-master-$(hostname).log"]

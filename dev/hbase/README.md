# HBase Development Environment

```bash
make          # Start HBase + Actionbase CLI (Ctrl+C to stop)
make hbase    # Start HBase only (host access via localhost)
make clean    # Remove all data
```

## URLs

- http://localhost:16010 - HBase UI
- http://localhost:8080 - Actionbase API

## Logs

```bash
cat data/logs/startup.log       # HBase
cat data/logs/actionbase.log    # Actionbase
```

## Config

`conf/application-local.yaml` - Actionbase settings
`conf/hbase-site.local.xml` - HBase config for localhost access (host -> container)
`conf/hbase-site.docker.xml` - HBase config for container-to-container access

# Roadmap

No timeline commitments. Items may shift based on community feedback.

- 📦 `pub` Already built internally, needs open-source release or documentation
- 💪 `new` New development

## Features

- [ ] 📦 `pub` Real-time aggregation
- [ ] 💪 `new` Bounded multi-hop queries
- [ ] 💪 `new` Real-time top-N queries (leaderboards)
- [ ] 💪 `new` Metadata v2 → v3
- [ ] 💪 `new` Metastore consolidation

## Ecosystem

- [ ] 📦 `pub` Migration pipeline (Spark-based HBase bulk loading)
- [ ] 📦 `pub` Async processor (Spark Streaming, WAL-driven)
- [ ] 📦 `pub` Shadow testing (mirroring traffic to a test cluster)

## Production

- [ ] 📦 `pub` 1.0.0 release (production-ready for external users)
- [ ] 📦 `pub` Provisioning guide for Kubernetes
- [ ] 📦 `pub` Configuration reference
- [ ] 📦 `pub` Operations and monitoring guide
- [ ] 📦 `pub` Test-driven documentation (tests as contracts)
- [ ] 📦 `pub` Zero-downtime migration (legacy → Actionbase)
- [ ] 💪 `new` Zero-downtime migration (storage backend migration)

## Exploring

- [ ] 💪 `new` Lightweight deployment without HBase and Kafka (e.g., [SlateDB](https://github.com/slatedb/slatedb), [S2](https://github.com/s2-streamstore/s2))

## Not Planned

- General-purpose graph queries
- Unbounded traversal or analytics
- Building another storage engine

## Feedback

Have feedback on the roadmap? Join the discussion on [GitHub Discussions](https://github.com/kakao/actionbase/discussions).

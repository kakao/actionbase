# Actionbase

> 🚀 **Open-sourced** — [Learn more](https://actionbase.io/blog/open-source-announcement/)

Likes, recent views, follows—look simple, but get complex as you scale, and end up rebuilt again and again.

Actionbase is a database for serving these user interactions at scale. Currently backed by HBase, built at Kakao, handling millions of requests per minute at peak for years.

## Quick Start

```bash
docker run -it --pull always ghcr.io/kakao/actionbase:standalone
```

**Write: Insert 3 edges with metadata** (via preset)
```
actionbase> load preset likes
  │ 3 edges inserted
  │  - Alice → Phone
  │  - Alice → Laptop
  │  - Bob → Phone
```

At write time, Actionbase precomputes everything for reads—no aggregation needed at query time.

**Read: Query precomputed results**
```
actionbase(likes:likes)> get --source Alice --target Phone
  │ → GET /graph/v3/databases/likes/tables/likes/edges/get?source=Alice&target=Phone
  │ ← 200 OK {"edges":[{"version":1737377177245,"source":"Alice","ta...
  │
  │ The edge is found: [Alice -> Phone]
  │ |---------------|--------|--------|---------------------------|
  │ | VERSION       | SOURCE | TARGET | PROPERTIES                |
  │ |---------------|--------|--------|---------------------------|
  │ | 1737377177245 | Alice  | Phone  | created_at: 1737377177245 |
  │ |---------------|--------|--------|---------------------------|

actionbase(likes:likes)> scan --index created_at_desc --start Alice --direction OUT
  │ → GET /graph/v3/databases/likes/tables/likes/edges/scan/created_at_desc?direction=OUT&limit=25&start=Alice
  │ ← 200 OK {"edges":[{"version":1737377177297,"source":"Alice","ta...
  │
  │ The 2 edges found (offset: -, hasNext: false)
  │ |---|---------------|--------|--------|---------------------------|
  │ | # | VERSION       | SOURCE | TARGET | PROPERTIES                |
  │ |---|---------------|--------|--------|---------------------------|
  │ | 1 | 1737377177297 | Alice  | Laptop | created_at: 1737377177297 |
  │ | 2 | 1737377177245 | Alice  | Phone  | created_at: 1737377177245 |
  │ |---|---------------|--------|--------|---------------------------|

actionbase(likes:likes)> count --start Alice --direction OUT
  │ → GET /graph/v3/databases/likes/tables/likes/edges/counts?start=Alice&direction=OUT
  │ ← 200 OK {"counts":[{"start":"Alice","direction":"OUT","count":2...
  │
  │ The count of 1 edges found
  │ |---|-------|-----------|-------|
  │ | # | START | DIRECTION | COUNT |
  │ |---|-------|-----------|-------|
  │ | 1 | Alice | OUT       | 2     |
  │ |---|-------|-----------|-------|
```

See [Quick Start](https://actionbase.io/quick-start/) for more details, or [Build Your Social Media App with Actionbase](https://actionbase.io/guides/build-your-social-media-app/) to go deeper.

## How It Works

Actionbase serves interaction-derived data that powers feeds, product listings, recommendations, and other user-facing surfaces.

Interactions are modeled as: **who** did **what** to which **target**

At write time, Actionbase precomputes everything needed for reads—accurate counts, consistent toggles, and ordering information for sorting and querying. At read time, there's no aggregation or additional computation. You simply read the precomputed results as they are.

Supported operations focus on high-frequency access patterns:

* Edge lookups (GET, multi-get)
* Edge counts (COUNT)
* Indexed edge scans (SCAN)

## When (Not) to Use It

Use Actionbase when:
- A single database no longer scales for your workload
- Interaction features are rebuilt repeatedly across teams
- You need predictable read latency without read-time computation

If a single database can handle your workload, that's the better choice.

## Architecture

Actionbase writes to HBase for storage and emits a WAL to Kafka for recovery, replay, and downstream pipelines. HBase provides strong durability and horizontal scalability.

```
Client ──> Actionbase ──> HBase (Storage for user interactions)
               │
               ├──> JDBC (Metastore, to be consolidated to HBase)
               │
               └──> Kafka (WAL/CDC) ──> Downstream Pipelines
```

Additional storage backends are planned for small to mid-size deployments.

## Codebase Overview

* **core** — Data model, mutation, query, encoding logic (Java, Kotlin)
* **engine** — Storage and messaging bindings (Kotlin)
* **server** — REST API server (Kotlin, Spring WebFlux)
* **pipeline** *(planned)* — Bulk loading and CDC processing (Scala, Spark)

## Current Status

Early open-source preparation phase. The first release focuses on introducing core concepts and hands-on guides. Production installation, operations guides, and additional components will be released over time.

## Contribute

We welcome contributions. See our [Contributing Guide](https://actionbase.io/community/contributing/).

For questions, ideas, or feedback, join us on [GitHub Discussions](https://github.com/kakao/actionbase/discussions/).

## Learn More

* [Documentation](https://actionbase.io/)
* [Actionbase at if(kakaoAI) 2024](https://www.youtube.com/watch?v=8-hVAFVHISE) (YouTube, Korean)

## License

This software is licensed under the [Apache 2 license](LICENSE).

Copyright 2026 Kakao Corp. <http://www.kakaocorp.com>

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this project except in compliance with the License. You may obtain a copy
of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.

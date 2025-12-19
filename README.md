# Actionbase

Actionbase is a system for storing and serving user activity data in real time.

## Overview

Actionbase is built for high-throughput, low-latency workloads where user
activity is continuously written and queried. It supports patterns such as
**recent views**, **likes** and **reactions**, and **follows** used across product listings,
recommendations, feeds, and other activity-driven surfaces in large-scale Kakao services.

User activity forms actor→target relationships with interaction properties.
Actionbase models them as a graph and creates read-optimized structures at write
time for fast, predictable queries.

When backed by HBase, Actionbase inherits strong durability and horizontal
scalability while providing a high-level abstraction tailored for real-time
activity serving.

### Design Goals

- **Shared Activity Layer**  
  Provide a unified platform for storing and serving user activity so individual
  services don’t need to build their own activity storage or serving logic.

- **Natural Activity Modeling**  
  Express activity as actor→target relationships with schema-defined
  properties, fitting the structure of user interactions.

- **Write-Time Optimization**  
  Capture common read patterns—recency, existence checks, counts,
  aggregations—at write time instead of reimplementing them per service.

- **Leverage Proven Storage**  
  Build on the strengths of existing storage engines (e.g., HBase) while
  focusing Actionbase on activity modeling rather than reinventing durability,
  scale, or distribution.

### Key Features

- **Write-Time Materialization**  
  Builds the data needed for fast, predictable reads at write time, eliminating
  service-specific indexing or counting logic.

- **Activity-Oriented Graph Model**  
  Represents activity as actor→target relationships with schema-defined
  properties.

- **Unified REST API**  
  Provides a simple, storage-agnostic interface for querying and mutating
  activity data.

- **WAL/CDC Integration**  
  Emits write-ahead and change logs for recovery, bulk loading,
  asynchronous processors, and downstream data pipelines.

## Architecture

Actionbase is built with a modular architecture:

- **core** (codec-java, core-java): Core data model definition and data processing logic
  - Java, Kotlin (Java 8 compatible) for compatibility (e.g., pipeline)
  - Data encoding/decoding for physical storage
  - Event and state change processing
  
- **engine**: Business logic engine
  - Kotlin
  - Pure business logic independent of transport protocols
  - HBase communication, metadata management, data mutation, and query execution

- **server**: High-performance REST API server
  - Kotlin, Spring WebFlux
  - Asynchronous API processing

- **pipeline** (Planned): Data processing
  - Scala (Java 8), Apache Spark
  - Async Processing, Bulk loading, backup, and real-time ETL

### Datastore

Actionbase currently uses HBase as its primary storage backend, leveraging its
durability and horizontal scalability. Additional storage backends, such as
SlateDB, are planned for future releases.

## Production Usage

Actionbase is used across Kakao services—for example, KakaoTalk and
KakaoShopping—to power real-time activity data processing with HBase. It has
been in stable production for years, delivering predictable reads, consistent
writes, and reliable handling of multi-terabyte datasets.

## Learn More

- [Documentation](https://actionbase.io/)
- [Introduction to Actionbase (Korean) / if(kakaoAI)2024](https://www.youtube.com/watch?v=8-hVAFVHISE)

## Contributing

We welcome contributions! Please see our contributing guidelines for details on:

- Code style and conventions
- Submitting issues and pull requests
- Development workflow

For more information, please visit our [Community](https://actionbase.dev/community/github/) page.

## Current Status

Actionbase is in its initial open-source preparation phase. The first release
focuses on introducing the core concepts and providing a hands-on guide, with
additional components to be open-sourced over time.

We are releasing the codebase largely as it evolved inside Kakao—after removing
sensitive details—to share its real development journey and grow it further
with community. Some internal modules and operational guides—including Kubernetes
and HBase—will be added later.

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

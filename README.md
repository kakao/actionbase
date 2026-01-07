# Actionbase

> 🚀 **Open-sourced** — [Learn more](https://actionbase.io/blog/open-source-announcement/)

## Introduction

Actionbase is a database for serving user interactions, used in production at Kakao.

It is designed for high-throughput, low-latency workloads where user
interactions are continuously written and queried. Actionbase focuses on serving interaction-derived data—such as **recent views**, **likes**, **reactions**,
and **follows**—that power product listings, recommendations, feeds, and other interaction-driven surfaces in large-scale services.

User interactions naturally form actor→target relationships with associated properties. Actionbase models
these relationships using a graph data model and materializes read-optimized structures at write time, enabling
fast and predictable queries without expensive read-time computation.

When backed by HBase, Actionbase inherits strong durability and horizontal scalability, and provides
a higher-level abstraction tailored for real-time interaction serving.

## Getting Started

- **Quick Start**  
  Get Actionbase running quickly with minimal setup.  
  → https://actionbase.io/quick-start/

- **Hands-on Guide: Build Your Social Media App**  
  A step-by-step guide that walks through modeling and serving real-world user interactions using Actionbase.  
  → https://actionbase.io/guides/build-your-social-media-app/

## Design Goals

- **Shared Interaction Layer**  
  Provide a unified platform for storing and serving user interactions, removing the need for individual services to
  build and operate their own interaction logic.

- **Natural Interaction Modeling**  
  Model interactions as actor→target relationships with schema-defined properties, closely reflecting how user
  interactions appear in real applications.

- **Write-Time Optimization**  
  Pre-compute common read patterns—such as retrieving recent items, checking existence, counting relationships,
  and traversing ordered results—at write time to enable fast and predictable reads.

- **Leverage Proven Storage**  
  Build on the strengths of existing storage engines (for example, HBase), handling interaction mutations at
  a higher level to produce durable state and read-optimized structures without reimplementing durability,
  scalability, or distribution.

## Key Features

- **Write-Time Materialization**  
  Pre-compute the data required for fast, predictable reads at write time, eliminating service-specific indexing and counting logic.

- **Interaction-Oriented Graph Model**  
  Model user interactions as actor→target relationships with schema-defined properties.

- **Unified REST API**  
  Expose a simple, storage-agnostic API for querying and mutating interaction data.

- **WAL / CDC Integration**  
  Emit write-ahead logs and change data capture streams for recovery, replay, asynchronous processing, and downstream data pipelines.

## Architecture

Actionbase is built with a modular architecture:

- **core** (codec-java, core-java)  
  Core data model definitions and processing logic
  - Java, Kotlin (Java 8 compatible)
  - Data encoding and decoding for physical storage
  - Event and state transition processing

- **engine**  
  Business logic engine
  - Kotlin
  - Core interaction processing independent of transport protocols
  - Metadata management, data mutation, and query execution

- **server**  
  High-performance REST API server
  - Kotlin, Spring WebFlux
  - Asynchronous request handling

- **pipeline** *(planned)*  
  Data processing and background workloads
  - Scala (Java 8), Apache Spark
  - Asynchronous processing, bulk loading, backup, and real-time ETL

## Datastore

Actionbase currently uses HBase as its primary storage backend, leveraging its durability and horizontal scalability.
Additional storage backends, such as SlateDB, are planned for future releases.

## Production Usage

Actionbase is used across Kakao services—including KakaoTalk and KakaoShopping—to power real-time user interaction serving
for tens of millions of users. It has been running in stable production for over two years, delivering predictable reads,
consistent writes, and reliable handling of multi-terabyte datasets on HBase.

## Learn More

- [Documentation](https://actionbase.io/)
- [Introduction to Actionbase (Korean) / if(kakaoAI) 2024](https://www.youtube.com/watch?v=8-hVAFVHISE)

## Contributing

We welcome contributions. For details on how to contribute, including code style, submitting issues and pull requests, and development workflow, see our [Contributing](https://actionbase.io/community/contributing/) page.

## Current Status

Actionbase is in its initial open-source preparation phase. The first release focuses on introducing the core concepts and
providing a hands-on guide, with additional components to be open-sourced over time.

The codebase is being released largely as it evolved inside Kakao, with sensitive details removed. Some internal modules and
operational guides—including Kubernetes and HBase—will be added in future releases.

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

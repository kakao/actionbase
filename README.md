# Actionbase

Actionbase is a system for storing and serving user activity data in real time.

## Overview

Actionbase is built for high-throughput, low-latency workloads where user
activity is continuously written and queried. It supports patterns such as
recent views, likes and reactions, follows, and real-time behavioral signals
used across product listings, recommendations, feeds, and other activity-driven
surfaces in large-scale Kakao services.

User activity forms actor→target relationships with interaction properties.
Actionbase models them as a graph and creates read-optimized structures at write
time for fast, predictable queries.

Built on HBase, it provides strong durability and horizontal scalability while
offering a high-level abstraction tailored for real-time activity serving.

### Project Goals

- **Real-time Serving**: Sub-10ms latency for most read operations
- **High Throughput**: Supports hundreds of thousands of requests per second (RPS)
- **Massive Data Handling**: Efficient management of multi-TB datasets
- **Horizontal Scalability**: Scales out seamlessly with service growth

### Key Features

- **Write-time Optimization**: Pre-computed indexes and counters for ultra-fast reads
- **Property Graph Model**: Naturally represents user activities and relationships
- **Easy Integration**: REST API for simple and seamless integration
- **Lambda Architecture Support**: Bulk loading, asynchronous processing, and data pipeline capabilities

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

- **pipeline**: Data processing *()*
  - Scala (Java 8), Apache Spark
  - Async Processing, Bulk loading, backup, and real-time ETL

### Datastore

Actionbase currently uses HBase as its primary storage backend due to its
reliability and horizontal scalability. Additional storage backends, such as
SlateDB, are planned for future releases.

## Production Usage

Actionbase is deployed in KakaoTalk and KakaoShopping, powering real-time
activity data processing for tens of millions of users. It has been in stable
production for years, delivering predictable read performance, consistent write
throughput, and robust multi-terabyte data management.

## Learn More

- [Documentation](https://actionbase.dev/)
- [Introduction to Actionbase (Korean) / if(kakaoAI)2024](https://www.youtube.com/watch?v=8-hVAFVHISE)

## Contributing

We welcome contributions! Please see our contributing guidelines for details on:

- Code style and conventions
- Submitting issues and pull requests
- Development workflow

For more information, please visit our [Community](https://actionbase.dev/community/github/) page.

## Current Status

This project is in the initial open-source preparation phase. Internal components
will be released progressively.

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

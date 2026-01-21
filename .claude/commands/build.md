---
description: Build the entire project (Gradle + Go CLI).
---

# Build Command

Build all project modules.

## Commands

### Full Build

```bash
# Kotlin/Java (Gradle)
./gradlew build

# Go CLI
cd cli && go build ./...
```

### Build Specific Module

```bash
# Core only
./gradlew :core:build

# Server only
./gradlew :server:build

# CLI only
cd cli && go build -o actionbase-cli ./cmd/actionbase
```

### Clean Build

```bash
# Gradle
./gradlew clean build

# Go
cd cli && go clean && go build ./...
```

### Build Without Tests

```bash
./gradlew build -x test
```

## Docker Build

```bash
# Standalone image
cd docker/standalone
./build-and-run.sh

# Or manually
docker build -t actionbase:latest .
```

## Expected Output

```
> Task :core:compileKotlin
> Task :core:compileJava
> Task :engine:compileKotlin
> Task :server:compileKotlin
> Task :server:bootJar

BUILD SUCCESSFUL in 45s

cli/
go: building ...
```

## Troubleshooting

### Compilation Errors
1. Check error message
2. Use `/build-fix` command
3. Check dependency versions

### Dependency Issues
```bash
./gradlew dependencies
./gradlew dependencyInsight --dependency <name>

cd cli && go mod tidy
cd cli && go mod download
```

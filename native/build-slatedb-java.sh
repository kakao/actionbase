#!/bin/bash
#
# Build slatedb-java from source and publish to Maven local.
#
# This script clones the slatedb repository (java-include-libs branch),
# builds the Java JAR with bundled native libraries, and publishes to
# ~/.m2/repository so Gradle can resolve it as a local dependency.
#
# Requirements:
#   - Rust toolchain (cargo)
#   - Java 24+ (for jextract / FFI support)
#   - Git
#
# Usage:
#   ./native/build-slatedb-java.sh           # clone + build + publish
#   ./native/build-slatedb-java.sh --clean   # remove clone and rebuild
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SLATEDB_DIR="${SCRIPT_DIR}/slatedb"
SLATEDB_JAVA_DIR="${SLATEDB_DIR}/slatedb-java"
REPO_URL="https://github.com/slatedb/slatedb.git"
BRANCH="java-include-libs"

# --- Parse arguments ---

if [[ "${1:-}" == "--clean" ]]; then
    echo "Cleaning previous clone..."
    rm -rf "${SLATEDB_DIR}"
fi

# --- Prerequisites ---

check_command() {
    if ! command -v "$1" &>/dev/null; then
        echo "Error: '$1' is required but not found in PATH."
        exit 1
    fi
}

check_command git
check_command cargo
check_command java

JAVA_VERSION=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [[ "${JAVA_VERSION}" -lt 24 ]]; then
    echo "Error: Java 24+ is required for slatedb-java (found Java ${JAVA_VERSION})."
    echo "Set JAVA_HOME to a Java 24+ installation."
    exit 1
fi

echo "Prerequisites OK: git, cargo, java ${JAVA_VERSION}"

# --- Clone or update ---

if [[ -d "${SLATEDB_DIR}" ]]; then
    echo "Using existing clone at ${SLATEDB_DIR}"
    cd "${SLATEDB_DIR}"
    git fetch origin "${BRANCH}"
    git checkout "${BRANCH}"
    git reset --hard "origin/${BRANCH}"
else
    echo "Cloning ${REPO_URL} (branch: ${BRANCH})..."
    git clone --branch "${BRANCH}" --depth 1 "${REPO_URL}" "${SLATEDB_DIR}"
fi

# --- Build ---

echo "Building slatedb-java..."
cd "${SLATEDB_JAVA_DIR}"
./gradlew build -x test

# --- Publish to Maven local ---
#
# The upstream build.gradle does not include the maven-publish plugin,
# so we inject it via a Gradle init script.

INIT_SCRIPT=$(mktemp)
trap 'rm -f "${INIT_SCRIPT}"' EXIT

cat > "${INIT_SCRIPT}" << 'INIT'
allprojects {
    apply plugin: 'maven-publish'

    afterEvaluate {
        publishing {
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
    }

    // Suppress Gradle module metadata so consumers don't see the
    // org.gradle.jvm.version=24 attribute, which would block resolution
    // from projects using a lower toolchain version.
    tasks.withType(GenerateModuleMetadata) {
        enabled = false
    }
}
INIT

echo "Publishing to Maven local..."
./gradlew publishToMavenLocal --init-script "${INIT_SCRIPT}"

# --- Verify ---

GAV="io.slatedb:slatedb:0.1.0-SNAPSHOT"
LOCAL_REPO="${HOME}/.m2/repository"
ARTIFACT_DIR="${LOCAL_REPO}/io/slatedb/slatedb/0.1.0-SNAPSHOT"

if [[ -d "${ARTIFACT_DIR}" ]]; then
    echo ""
    echo "Published ${GAV} to Maven local:"
    ls -la "${ARTIFACT_DIR}"/*.jar 2>/dev/null || true
    echo ""
    echo "Done. You can now run: ./gradlew :engine:build"
else
    echo "Error: Expected artifact not found at ${ARTIFACT_DIR}"
    exit 1
fi

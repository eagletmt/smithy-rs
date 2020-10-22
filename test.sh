#!/bin/bash
set -e
# Used by codegen-test/smithy-build.json
declare REPO_ROOT
REPO_ROOT="$(git rev-parse --show-toplevel)"
export REPO_ROOT
./gradlew test
./gradlew ktlintFormat
./gradlew ktlint
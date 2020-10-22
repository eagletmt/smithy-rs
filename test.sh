#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0.
#

set -e
declare REPO_ROOT
REPO_ROOT="$(git rev-parse --show-toplevel)"
export REPO_ROOT
./gradlew test
./gradlew ktlint
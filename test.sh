set -e
declare REPO_ROOT
REPO_ROOT="$(git rev-parse --show-toplevel)"
export REPO_ROOT
./gradlew test
./gradlew ktlint
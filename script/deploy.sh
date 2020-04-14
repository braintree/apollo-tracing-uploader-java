#!/bin/bash

set -euo pipefail

branch="${BRANCH:-master}"
if [[ "$(git rev-parse --abbrev-ref HEAD)" != "$branch" ]]; then
  echo "Must be run from $branch (set \$BRANCH to override)" >&2
  exit 1
fi

cd -P "$(dirname "$0")/.."
version="$(./gradlew --console=plain properties | grep version: | cut -d' ' -f2)"

case "${1:-}" in
  snapshot)
    if [[ "$version" != *-SNAPSHOT ]]; then
      echo "Skipping non-snapshot version $version" >&2
      exit 0 # Don't fail travis stage
    fi

    echo "Deploying $version"
    exec ./gradlew clean uploadArchives
    ;;
  release)
    if [[ "$version" == *-SNAPSHOT ]]; then
      echo "$version is not a release version" >&2
      exit 1
    fi

    echo "Deploying $version"
    exec ./gradlew clean uploadArchives closeAndReleaseRepository
    ;;
  *)
    echo "Usage: $(basename $0) [snapshot|release]" >&2
    exit 1
    ;;
esac

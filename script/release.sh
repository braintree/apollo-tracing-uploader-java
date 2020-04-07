#!/bin/bash

set -eu

printf "Sonatype password: "
read -s SONATYPE_PASSWORD
export SONATYPE_PASSWORD

./gradlew clean uploadArchives closeAndReleaseRepository

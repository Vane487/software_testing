#!/usr/bin/env bash
set -euo pipefail

LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_DIR"

echo "1/3 Build"
mvn -B -DskipTests package

echo "2/3 Unit tests"
mvn -B -Dtest=EnrollmentServiceUnitTest,IntentionalFailureDemoTest test

echo "3/3 Integration tests"
mvn -B -Pregression test

echo "Local CI equivalent passed."

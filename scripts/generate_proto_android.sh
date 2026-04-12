#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_DIR="$REPO_ROOT/proto"
OUT_ANDROID_DIR="$REPO_ROOT/android-app/app/build/generated/proto/java"

if ! command -v protoc >/dev/null 2>&1; then
  echo "Error: protoc is not installed."
  echo "Install it first, then re-run this script."
  echo "Fedora: sudo dnf install protobuf-compiler"
  echo "Ubuntu: sudo apt install protobuf-compiler"
  exit 1
fi

echo "Generating Android Java protobuf files..."
mkdir -p "$OUT_ANDROID_DIR"
protoc \
  --proto_path="$PROTO_DIR" \
  --java_out="$OUT_ANDROID_DIR" \
  "$PROTO_DIR"/*.proto

echo "Done."
find "$OUT_ANDROID_DIR" -type f -name "*.java" | wc -l | awk '{print "Generated Java files: "$1}'

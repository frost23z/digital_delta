#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_DIR="$REPO_ROOT/proto"
OUT_GO_DIR="$REPO_ROOT/sync-server/internal/gen/proto"
DESCRIPTOR_OUT="/tmp/digital_delta_proto.desc"

if ! command -v protoc >/dev/null 2>&1; then
  echo "Error: protoc is not installed."
  echo "Install it first, then re-run this script."
  echo "Fedora: sudo dnf install protobuf-compiler"
  echo "Ubuntu: sudo apt install protobuf-compiler"
  exit 1
fi

if ! command -v protoc-gen-go >/dev/null 2>&1; then
  echo "Error: protoc-gen-go is not installed."
  echo "Install: go install google.golang.org/protobuf/cmd/protoc-gen-go@latest"
  exit 1
fi

if ! command -v protoc-gen-go-grpc >/dev/null 2>&1; then
  echo "Error: protoc-gen-go-grpc is not installed."
  echo "Install: go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest"
  exit 1
fi

echo "Generating Go protobuf files..."
mkdir -p "$OUT_GO_DIR"

# Clean stale generated files from previous runs.
find "$OUT_GO_DIR" -maxdepth 1 -type f -name "*.pb.go" -delete

protoc \
  --proto_path="$PROTO_DIR" \
  --go_out=paths=source_relative:"$OUT_GO_DIR" \
  --go-grpc_out=paths=source_relative:"$OUT_GO_DIR" \
  --descriptor_set_out="$DESCRIPTOR_OUT" \
  "$PROTO_DIR"/*.proto

echo "Done."
ls -lh "$DESCRIPTOR_OUT"

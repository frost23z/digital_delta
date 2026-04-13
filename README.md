# Digital Delta

Digital Delta is an offline-first disaster logistics system prototype for HackFusion 2026.
It is designed for flood-response operations where connectivity, routes, and delivery certainty are unstable.

## Core Idea

Digital Delta keeps frontline operations usable in degraded conditions by combining:

- Offline-first Android workflow and local persistence
- Sync backbone with gRPC + Protobuf default path
- Route recomputation from live chaos events
- Signed proof-of-delivery handshake controls

## Project Components

- android-app: Android client (Jetpack Compose + SQLDelight)
- sync-server: Go sync service (gRPC primary + HTTP fallback path)
- chaos: Flask simulation API for flood and recede edge events
- proto: Shared contract definitions
- scripts: Protobuf generation helpers

## Quick Run

1. Start the sync server:

```bash
cd sync-server
go run ./cmd
```

2. Start the chaos API:

```bash
cd chaos
python chaos_server.py
```

3. Launch the Android app from Android Studio:

- Open android-app
- Run app module on emulator or device

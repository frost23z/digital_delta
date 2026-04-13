# Digital Delta Architecture

Last updated: 2026-04-13

## High-Level Diagram

```mermaid
flowchart TB
    OP[Field Operator]

    subgraph APP[Android App]
        UI[Compose Screens]
        AUTH[OfflineAuthManager\nIdentity + OTP]
        ROUTE[Routing and Triage Logic]
        POD[Signed PoD Logic]
        CRDT[Mutation and Conflict Logic]
        DB[(Local SQLite\nSQLDelight)]
        TX[Sync Transport Layer]
    end

    subgraph SERVER[Sync Server (Go)]
        G[gRPC SyncService]
        H[HTTP JSON Sync API\nDev fallback]
        S[(Server SQLite)]
    end

    subgraph CHAOS[Chaos Engine (Flask)]
        API[/api/network/status]
        MAP[(sylhet_map.json)]
    end

    OP --> UI
    UI --> AUTH
    UI --> ROUTE
    UI --> POD
    UI --> CRDT
    AUTH --> DB
    ROUTE --> DB
    POD --> DB
    CRDT --> DB
    CRDT --> TX
    TX -->|Primary| G
    TX -->|Fallback| H
    G --> S
    H --> S
    ROUTE --> API
    API --> MAP
```

## Runtime Flow

1. The Android app records operations locally first.
2. Mutations are queued for sync once connectivity is available.
3. Sync defaults to gRPC + Protobuf with HTTP JSON as explicit fallback path.
4. Route logic polls chaos status and recomputes feasible paths under disruption.
5. PoD actions and auth events stay traceable through mutation records.

## Boundaries

1. Device trust boundary: identity keys remain local on device.
2. Transport boundary: mixed path currently exists and should be disclosed as partial compliance.
3. Simulation boundary: chaos API is a demonstration dependency.

## Current Constraints

1. Full transport compliance is pending peer path migration.
2. Load benchmark evidence is not committed yet.
3. Predictive route decay and fleet handoff are roadmap modules.

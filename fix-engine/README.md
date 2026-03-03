# Distributed FIX Engine

A production-grade, distributed FIX protocol engine built with **QuickFIX/J**, **Spring Boot 3**, **Hazelcast**, and **ZooKeeper**. Supports multiple simultaneous FIX sessions (both initiator and acceptor), 3-node active/standby clustering with automatic failover, RDBMS message logging, and a React GUI.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              GUI (React)  ←  REST / WebSocket                   │
└───────────────────────┬─────────────────────────────────────────┘
                        │
            ┌───────────▼───────────┐
            │   HAProxy (port 443)  │
            └──┬──────────┬────────┘
               │          │
      ┌────────▼──┐   ┌───▼────────┐   ┌────────────┐
      │  Node 1   │   │  Node 2   │   │  Node 3   │
      │ (ACTIVE)  │   │ (STANDBY) │   │ (STANDBY) │
      │ QuickFIX/J│◄─►│ QuickFIX/J│◄─►│ QuickFIX/J│
      │Spring Boot│   │Spring Boot│   │Spring Boot│
      └─────┬─────┘   └────┬──────┘   └─────┬─────┘
            └──────────────┼────────────────┘
                           │
              ┌────────────▼────────────┐
              │   Hazelcast Cluster     │  ← Session state, seq numbers
              │   ZooKeeper Ensemble    │  ← Leader election
              │   PostgreSQL            │  ← Message persistence
              └─────────────────────────┘
```

---

## Technology Stack

| Component | Technology |
|---|---|
| FIX Engine | QuickFIX/J 2.3.1 |
| Framework | Spring Boot 3.2 |
| Cluster/Failover | Apache ZooKeeper + Curator |
| Distributed Cache | Hazelcast 5.3 |
| Database | PostgreSQL 15 |
| Migrations | Flyway |
| GUI | React 18 + TypeScript |
| API Docs | SpringDoc OpenAPI (Swagger UI) |

---

## Quick Start (Docker Compose)

```bash
# 1. Build the JAR
mvn clean package -DskipTests

# 2. Start the full stack (3 nodes + postgres + zookeeper + haproxy)
docker-compose up -d

# 3. Check health
curl http://localhost:8081/actuator/health

# 4. Get auth token
curl -X POST http://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 5. List sessions
curl http://localhost/api/v1/sessions \
  -H "Authorization: Bearer <token>"

# 6. Open Swagger UI
open http://localhost:8081/swagger-ui/index.html
```

---

## Production Deployment (3 Unix Hosts)

### Prerequisites on each host

```bash
# Java 17+
sudo apt install openjdk-17-jre-headless

# Create user and directories
sudo useradd -r -s /bin/false fixengine
sudo mkdir -p /opt/fix-engine/{bin,conf,lib,logs,data}
sudo chown -R fixengine:fixengine /opt/fix-engine
```

### Deploy

```bash
# Copy JAR
scp target/fix-engine-2.1.0.jar fixengine@10.0.1.10:/opt/fix-engine/lib/
scp target/fix-engine-2.1.0.jar fixengine@10.0.1.11:/opt/fix-engine/lib/
scp target/fix-engine-2.1.0.jar fixengine@10.0.1.12:/opt/fix-engine/lib/

# Copy config
scp src/main/resources/application.yaml fixengine@10.0.1.10:/opt/fix-engine/conf/
# Repeat for nodes 11, 12

# Copy start/stop scripts
scp bin/start.sh bin/stop.sh fixengine@10.0.1.10:/opt/fix-engine/bin/
chmod +x /opt/fix-engine/bin/*.sh
```

### Start each node

```bash
# Node 1
ssh fixengine@10.0.1.10
NODE_ID=node-1 NODE_HOST=10.0.1.10 \
  DB_HOST=10.0.1.100 DB_PASS=yourpassword \
  ZK_CONNECT=10.0.1.10:2181,10.0.1.11:2181,10.0.1.12:2181 \
  /opt/fix-engine/bin/start.sh

# Node 2
ssh fixengine@10.0.1.11
NODE_ID=node-2 NODE_HOST=10.0.1.11 \
  DB_HOST=10.0.1.100 DB_PASS=yourpassword \
  ZK_CONNECT=10.0.1.10:2181,10.0.1.11:2181,10.0.1.12:2181 \
  /opt/fix-engine/bin/start.sh

# Node 3
ssh fixengine@10.0.1.12
NODE_ID=node-3 NODE_HOST=10.0.1.12 \
  DB_HOST=10.0.1.100 DB_PASS=yourpassword \
  ZK_CONNECT=10.0.1.10:2181,10.0.1.11:2181,10.0.1.12:2181 \
  /opt/fix-engine/bin/start.sh
```

---

## REST API Reference

| Method | Endpoint | Description | Role |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Login, returns JWT | Public |
| GET | `/api/v1/sessions` | List all sessions | VIEWER+ |
| POST | `/api/v1/sessions` | Create session | ADMIN |
| PUT | `/api/v1/sessions/{id}` | Update config | OPERATOR+ |
| DELETE | `/api/v1/sessions/{id}` | Delete session | ADMIN |
| POST | `/api/v1/sessions/{id}/start` | Start session | OPERATOR+ |
| POST | `/api/v1/sessions/{id}/stop` | Stop session | OPERATOR+ |
| POST | `/api/v1/sessions/{id}/reset` | Reset seq nums | OPERATOR+ |
| GET | `/api/v1/messages` | Search messages | VIEWER+ |
| GET | `/api/v1/messages/{id}` | Get parsed message | VIEWER+ |
| GET | `/api/v1/nodes` | Cluster node status | VIEWER+ |
| GET | `/api/v1/nodes/leader` | Current leader | VIEWER+ |

### Message Search Parameters

```
GET /api/v1/messages?sessionId=&msgType=D&direction=O&search=AAPL&page=0&size=50
```

---

## WebSocket Events

Connect to: `ws://host/ws/events`

Subscribe to topics:
- `/topic/messages` — all live FIX messages
- `/topic/sessions/{sessionId}` — per-session messages
- `/topic/session-status` — session connect/disconnect events

---

## Failover Behaviour

1. Node 1 (leader) crashes
2. ZooKeeper detects session timeout (~5 seconds)
3. Fastest remaining node wins leader election
4. New leader calls `restoreAndActivateSessions()`
5. Hazelcast IMap still has all session states (backed up on nodes 2 & 3)
6. Sessions reconnect with correct sequence numbers — counterparties see no gap
7. HAProxy health check removes the failed node from rotation automatically

---

## Adding a New FIX Session via GUI

1. Navigate to **Sessions** tab → click **+ New Session**
2. Fill in: Begin String, Sender CompID, Target CompID, Mode, Host (INITIATOR only), Port
3. Click **Create Session**
4. Click **▶ Start** to connect

Or via REST:

```bash
curl -X POST http://localhost/api/v1/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "SESSION-004",
    "beginString": "FIX.4.4",
    "senderCompId": "MY_FIRM",
    "targetCompId": "NEW_BROKER",
    "mode": "INITIATOR",
    "host": "192.168.1.200",
    "port": 9900,
    "heartbeatInterval": 30,
    "enabled": true
  }'
```

---

## Security

- All API endpoints require JWT bearer token
- Three roles: `VIEWER` (read-only), `OPERATOR` (start/stop/reset sessions), `ADMIN` (full access)
- Default users: `admin` / `admin123`, `viewer` / `viewer123` — **CHANGE IN PRODUCTION**
- Change `jwt.secret` in production to a randomly generated 256-bit key
- Enable TLS on FIX sessions via QuickFIX/J `SocketUseSSL=Y` session config

---

## Project Structure

```
fix-engine/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── bin/
│   ├── start.sh
│   └── stop.sh
├── deploy/
│   └── haproxy.cfg
└── src/
    ├── main/
    │   ├── java/com/fixengine/
    │   │   ├── FixEngineApplication.java
    │   │   ├── config/          ← Spring, QuickFIX, Hazelcast, Security configs
    │   │   ├── core/            ← Application adapter, parser, router
    │   │   ├── cluster/         ← ZooKeeper leader election, node registry
    │   │   ├── cache/           ← Hazelcast session state cache
    │   │   ├── entity/          ← JPA entities
    │   │   ├── repository/      ← Spring Data repos
    │   │   ├── service/         ← Session + message business logic
    │   │   ├── api/             ← REST controllers + security filters
    │   │   ├── dto/             ← Request/response DTOs
    │   │   └── websocket/       ← Live message publisher
    │   └── resources/
    │       ├── application.yaml
    │       ├── logback-spring.xml
    │       └── db/migration/    ← Flyway SQL migrations
    └── test/
        └── java/com/fixengine/ ← Unit tests
```

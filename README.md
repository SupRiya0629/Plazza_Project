# ⚡ EV Charging Network

A backend for an electric-vehicle charging network. A driver finds a nearby station
with a free connector, plugs in, charges, unplugs, and is billed for the energy
delivered using a tiered tariff.

- **Backend:** Scala + http4s REST API (in-memory storage)
- **Frontend:** a single HTML page that calls those APIs
- **Tests:** ScalaTest unit tests for the billing engine

---

## How to Run

### 1. Start the backend

```bash
bash run.sh
```

You should see:

```
⚡ EV Charging Network Server
📍 Running on: http://0.0.0.0:8080
```

### 2. Open the frontend

```bash
xdg-open web/index.html
```

Click the **Demo** tab → **Load Complete Demo** to create a driver, a station, two
connectors, and two promo codes on the server. Then use those IDs in the other tabs.

### 3. Run the tests

```bash
sbt test
```

---

## Architecture

The code is split into clear layers. Each layer only talks to the one below it.

```
┌──────────────────────────────┐
│  web/index.html              │  Browser UI — only calls the REST APIs (fetch)
└───────────────┬──────────────┘
                │ HTTP + JSON
┌───────────────▼──────────────┐
│  Server.scala                │  REST routes: parse JSON, call the service, return JSON
├──────────────────────────────┤
│  ChargingService.scala       │  Business rules: connector matching, sessions, promos
├──────────────────────────────┤
│  BillingEngine.scala         │  Pure tiered-pricing math (no state → easy to test)
├──────────────────────────────┤
│  Storage.scala               │  In-memory HashMaps (data lost on restart)
└──────────────────────────────┘

  Models.scala        — the data types (Driver, Station, Connector, Session, Tariff…)
  IdGenerator.scala   — readable IDs: DRV_001, STN_001, CON_001, SES_001
```

**Why this shape:**

- **Billing is a pure function** (`BillingEngine`). Given a connector type and energy,
  it returns a price. No I/O, no state — so the tests are simple and trustworthy.
- **Business rules live in one place** (`ChargingService`). Connector matching, the
  AC→DC fallback, and promo handling are all here, not scattered across routes.
- **Storage is isolated.** Swapping the HashMaps for a real database would only touch
  `Storage.scala`.

---

## Data Flow — a charging session

1. `POST /api/session/start` → `ChargingService.startChargingSession`
   finds a free connector of the requested type (falling back AC→DC), applies a promo
   if valid, and saves an **active** session.
2. `POST /api/session/end` → `ChargingService.endChargingSession`
   asks `BillingEngine` for the tiered bill based on the energy delivered, applies the
   stored discount, and marks the session **completed**.

---

## Billing (Tiered Tariff)

Each slab is charged at its own rate; the session never costs less than the minimum.

| Connector | Minimum | Slab 1 | Slab 2 | Slab 3 |
|-----------|---------|--------|--------|--------|
| **DC** (fast) | ₹150 | 0–10 kWh @ ₹20 | 10–25 kWh @ ₹14 | 25+ kWh @ ₹9 |
| **AC** (slow) | ₹80  | 0–5 kWh @ ₹15  | 5–15 kWh @ ₹12  | 15+ kWh @ ₹8 |

Examples:
- DC, 20 kWh → 10×20 + 10×14 = **₹340**
- AC, 20 kWh → 5×15 + 10×12 + 5×8 = **₹235**
- With a 20% promo on ₹340 → **₹272**

**AC billed on DC:** if a driver asks for AC and only DC is free, they charge on DC but
are billed at the cheaper AC tariff.

---

## API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/driver/register` | Register a driver |
| POST | `/api/vehicle/register` | Register a vehicle for a driver |
| POST | `/api/station/register` | Register a station at a location |
| POST | `/api/connector/add` | Add an AC/DC connector to a station |
| GET  | `/api/station/{id}/connectors` | List a station's connectors + status |
| POST | `/api/connector/{id}/deactivate` | Take a connector out of service |
| POST | `/api/connector/{id}/activate` | Bring a connector back into service |
| POST | `/api/session/start` | Start a charging session |
| POST | `/api/session/end` | End a session, get the bill |
| POST | `/api/nearby-stations` | Find stations within a radius |
| POST | `/api/promo/create` | Create a promo code |
| DELETE | `/api/promo/{code}` | Delete a promo code |
| GET  | `/api/history/driver/{id}` | A driver's sessions |
| GET  | `/api/history/station/{id}` | A station's sessions |

Example:

```bash
curl -X POST http://localhost:8080/api/driver/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Raj","email":"raj@test.com","phone":"9876543210"}'
```

---

## Project Structure

```
Plazza_Project/
├── build.sbt                                   Build config + dependencies
├── run.sh                                      Starts the server on port 8080
├── README.md                                   This file
├── web/
│   └── index.html                              Frontend (calls the REST APIs)
└── src/
    ├── main/
    │   ├── resources/simplelogger.properties   Keeps server logs quiet
    │   └── scala/evcharging/
    │       ├── Server.scala                     REST routes
    │       ├── ChargingService.scala            Business logic
    │       ├── BillingEngine.scala              Tiered pricing (pure)
    │       ├── Storage.scala                    In-memory storage
    │       ├── Models.scala                     Data types
    │       └── IdGenerator.scala                Readable IDs
    └── test/scala/evcharging/
        └── BillingEngineTest.scala              13 billing tests
```

---

## Assumptions & Design Decisions

- **In-memory storage.** Data is lost when the server stops. Keeps the focus on the
  domain; swapping in a database only touches `Storage.scala`.
- **One active session per connector.** A connector in use isn't offered again until
  its session ends.
- **Deactivating a connector** only blocks *new* sessions — it doesn't cancel an
  in-progress one.
- **AC→DC fallback** is billed at the AC (cheaper) tariff, as the spec requires.
- **Invalid promo codes are ignored** (session still starts) rather than failing —
  friendlier UX.
- **Billing is a pure function**, so tariff math is fully unit-tested.

### With more time
- Persist to a database; add concurrency safety for the "two drivers, last connector"
  race; input validation; peak-hour tariffs; and a pluggable station-selection strategy.

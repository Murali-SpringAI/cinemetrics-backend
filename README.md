# CineMetrics — Backend

**Real-Time Box Office Intelligence Agent**
Agentic Cinema Hackathon — ClickHouse Partner Track

[![Google Cloud](https://img.shields.io/badge/Google_Cloud-Run-4285F4)](https://cloud.google.com/run)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-Cloud-FFCC01)](https://clickhouse.cloud)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F)](https://spring.io)

---

## What It Does

CineMetrics answers questions studios can't answer today:

> *"Film X has been in theatres 3 weeks. Sentiment just dropped 15%. Should we pull it?"*

A Gemini agent queries ClickHouse in real time, reasons over box office + sentiment + streaming data, and returns a structured recommendation with supporting evidence.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Agent | Google Gemini 2.5 Flash (Vertex AI) |
| Analytics DB | ClickHouse Cloud (partner track) |
| Backend | Java 21 + Spring Boot 3.3 |
| Hosting | Google Cloud Run (free tier) |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- [ClickHouse Cloud account](https://clickhouse.cloud) (free 30-day trial)
- [Google Cloud account](https://cloud.google.com) with Vertex AI API enabled
- Docker (for Cloud Run deployment)

---

## Local Setup

### 1. Clone and configure

```bash
git clone https://github.com/your-team/cinemetrics-backend
cd cinemetrics-backend
cp .env.example .env
# Edit .env with your ClickHouse and GCP credentials
```

### 2. Set up ClickHouse Cloud

1. Create a free account at [clickhouse.cloud](https://clickhouse.cloud)
2. Create a new service — choose **AWS us-east-1** (lowest latency)
3. Create a database called `cinemetrics`
4. Copy the connection string into `.env` as `CLICKHOUSE_URL`

The app creates all tables automatically on first startup.

### 3. Set up Google Cloud

```bash
# Install gcloud CLI: https://cloud.google.com/sdk/docs/install
gcloud auth application-default login
gcloud services enable aiplatform.googleapis.com
```

Set `GCP_PROJECT_ID` in `.env` to your project ID.

### 4. Run locally

```bash
# Export env vars
export $(cat .env | grep -v '#' | xargs)

# Run with Maven
mvn spring-boot:run
```

The app starts on **http://localhost:8080**. On first startup it:
- Creates all 4 ClickHouse tables
- Seeds 10 demo films × 90 days of synthetic data (~45,000 rows)

---

## API Endpoints

### Agent

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/agent/query` | Ask the agent a natural language question |
| `GET` | `/api/agent/health` | Agent health check |

**Example query:**
```bash
curl -X POST http://localhost:8080/api/agent/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Should we extend Galactic Frontier? It is in week 4.", "filmIds": ["film_001"]}'
```

**Response:**
```json
{
  "answer": "Based on week 4 data, Galactic Frontier grossed $12.4M domestic...",
  "recommendation": "EXTEND",
  "confidence": 0.82,
  "riskFactors": ["Risk: sentiment declined 8% week-on-week"],
  "queriesExecuted": ["SELECT week_number, SUM(gross_usd)..."],
  "processingMs": 3240
}
```

### Analytics

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/analytics/briefing` | Daily studio briefing (all films) |
| `GET` | `/api/analytics/films` | List all films |
| `GET` | `/api/analytics/film/{filmId}` | Full analytics for one film |
| `POST` | `/api/analytics/briefing/refresh` | Force-regenerate briefing |

---

## Cloud Run Deployment (Free)

### One-time setup

```bash
# Set your project
gcloud config set project YOUR_PROJECT_ID

# Enable required APIs
gcloud services enable run.googleapis.com containerregistry.googleapis.com
```

### Deploy

```bash
# Build and push image
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/cinemetrics-backend

# Deploy to Cloud Run
gcloud run deploy cinemetrics-backend \
  --image gcr.io/YOUR_PROJECT_ID/cinemetrics-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --set-env-vars "CLICKHOUSE_URL=jdbc:ch://...,CLICKHOUSE_USER=default,CLICKHOUSE_PASSWORD=...,GCP_PROJECT_ID=...,SEED_ON_STARTUP=true"
```

Cloud Run free tier: **2 million requests/month + 360,000 GB-seconds compute** — more than enough for the hackathon.

Your deployed URL: `https://cinemetrics-backend-XXXX-uc.a.run.app`

---

## Project Structure

```
src/main/java/com/cinemetrics/
├── CineMetricsApplication.java       # Entry point
├── agent/
│   └── GeminiAgentService.java       # Gemini tool-call loop (core)
├── config/
│   ├── ClickHouseConfig.java         # DataSource bean
│   └── CorsConfig.java               # CORS for Node.js frontend
├── controller/
│   ├── AgentController.java          # POST /api/agent/query
│   └── AnalyticsController.java      # GET /api/analytics/*
├── model/
│   ├── AgentRequest.java
│   ├── AgentResponse.java
│   ├── Film.java
│   ├── BoxOfficeDailyRecord.java
│   └── StudioBriefing.java
├── repository/
│   ├── ClickHouseQueryEngine.java    # SQL executor (agent tool backend)
│   └── SchemaInitialiser.java        # CREATE TABLE IF NOT EXISTS on startup
└── service/
    ├── BriefingService.java          # Daily briefing generation + cache
    ├── FilmContextService.java       # Film metadata queries
    └── SyntheticDataSeeder.java      # Demo data (10 films × 90 days)
```

---

## Demo Films

The seeder creates 10 realistic films:

| ID | Title | Budget | Genre |
|---|---|---|---|
| film_001 | Galactic Frontier | $180M | Sci-Fi |
| film_002 | The Last Accord | $45M | Drama |
| film_003 | Speed Protocol | $95M | Action |
| film_004 | Midnight Sonata | $8M | Romance |
| film_005 | Iron Colossus 4 | $250M | Action |
| film_006 | The Quiet Storm | $22M | Thriller |
| film_007 | Neon Dynasty | $78M | Sci-Fi |
| film_008 | A Family Reborn | $15M | Family |
| film_009 | Fracture Point | $55M | Thriller |
| film_010 | Legends of the Deep | $130M | Adventure |

---

## License

MIT — see [LICENSE](LICENSE)

# AeroCause frontend

React, Vite, TypeScript, Tailwind CSS, Recharts, React Router, TanStack Query, and lucide-react UI for the AeroCause API.

## Run locally

```powershell
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` and `/ai` to `http://localhost:8000`. Configure `VITE_API_BASE_URL` or `VITE_AI_API_BASE_URL` if the services use other URLs. The UI switches to explicitly labelled sample fixtures when the backend connection is unavailable; HTTP client errors remain visible instead of silently switching to fixtures.

## Pages

- **Incidents:** prioritized incident list, status and pollutant filters.
- **Incident detail:** readings chart, optional neighbouring-station overlays, ranked hypotheses, validated officer brief, evidence/QC flags, audit events, and officer review.
- **Replay:** choose a sample episode, set speed, and monitor processing.
- **Evaluation:** view expected-versus-actual outcomes from the latest evaluation API response. Fields not returned by the API are labelled “Not reported”.
- **Citizen view:** AQI summary, hedged citizen-summary endpoint text, language switch, explicit precaution placeholders, and a configured official portal link.
- **Sources & method:** describes enabled sources and limitations and reports retrieved passages without inventing a document catalogue.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Backend API base URL |
| `VITE_AI_API_BASE_URL` | `/ai` | AI service base URL |
| `VITE_POLL_INTERVAL_MS` | `10000` | Query refresh interval while a replay is running; queries are not polled while idle |
| `VITE_OFFICIAL_REPORT_URL` | `https://cpcb.nic.in/` | Configured public reporting/reference portal |

For Docker Compose, set `POLL_INTERVAL_MS` and `OFFICIAL_REPORT_URL` in the root `.env`; they are passed to the Vite build. The Nginx container proxies API routes to the backend and AI containers.

## Tests and build

```powershell
npm test
npm run build
```

The test suite includes component-level checks and a mock-data replay-to-brief-to-review path. Hindi and Marathi UI text is draft translation and flagged for native-speaker review. Category precautions are placeholders, not health advice; replace them only with reviewed official guidance before any public use.

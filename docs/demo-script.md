# AeroCause: 3-minute demo walkthrough

## Before the walkthrough

From the repository root, run `docker compose up --build` and wait for the services to become healthy. Open `http://localhost:5173` in a browser. Use the default MOCK provider and empty official-document corpus. No API key is required.

## Walkthrough (exact clicks and narration)

### 0:00–0:25 — Show the clean start

1. Open **Incidents**.
2. Point to **Start sample replay**, **SAMPLE DATA (synthetic)**, and the decision-support notice.

Say: “This is a clean, synthetic demo environment. It helps organize evidence for review; it does not identify a polluter or make an enforcement decision.”

### 0:25–1:10 — Replay and inspect an incident

1. Click **Start sample replay**.
2. On **Episode replay**, confirm **Dust Storm Event** is selected and click **Start replay**.
3. Wait for incidents to appear under **Recent incidents**, then click the incident row.
4. Click **Hypotheses**, then **Evidence & QC**.

Say: “The deterministic pipeline records readings, QC flags, ranked source-type hypotheses, and evidence that is for, against, or missing. These are screening heuristics, not validated scientific conclusions.”

### 1:10–1:45 — Generate and review the brief

1. Click **Officer brief**.
2. Click **Generate brief** if no brief exists.
3. Point out the **MOCK** badge, validator status, verification steps, and the no-official-source notice.
4. In the fixed review bar, keep **Needs more evidence**, enter a reviewer name, then click **Save review**.
5. Click **Timeline / Audit** to show the review event.

Say: “The model is in deterministic mock mode. The corpus is empty by default, so no official citation is claimed. The audit timeline records the review.”

### 1:45–2:20 — Run the guided demo sequence

1. Return to **Replay**.
2. Click **Run demo mode**.
3. Point out the status/progress while the app runs dust → sensor fault → ambiguous/INCONCLUSIVE.

Say: “Demo mode runs the recommended synthetic order. A sensor-fault flag is a quality-control signal, not proof of a physical cause. Ambiguous evidence should remain inconclusive.”

### 2:20–2:45 — Run evaluation

1. Click **Evaluation**.
2. Click **Run evaluation** and wait for the result.
3. Point out separate synthetic and real episode sections, first-attempt validator violations, final unsupported claims, citation validity, and time to brief.

Say: “This is a repeatable pipeline test. The synthetic expected labels are test metadata, not an accuracy claim. Real episodes are unlabelled unless a human supplies reference labels. Any final validator violation fails the build gate.”

### 2:45–3:00 — Citizen view and limits

1. Click **Citizen View**.
2. Point out the AQI disclaimer, hedged summary, language selector, and explicit precaution placeholder.

Say: “Public-facing health text is still a placeholder and must be replaced with reviewed official guidance. Translations also need native-speaker review.”

## Optional command-line evaluation

From a second terminal at the repository root:

```powershell
py backend/scripts/run_evaluation.py
```

It replays every configured episode, stores the latest run, writes CSV/Markdown reports under `docs/`, and exits nonzero if the final-brief gate fails.

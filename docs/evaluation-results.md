# Evaluation Results

- Run: 2026-11-10T12:52:07.024739700Z
- Build gate: FAIL
- Unsupported claims in final briefs: 2

> Synthetic episodes are pipeline fixtures, not an accuracy claim. Real episodes have no expected labels unless supplied by a human.

| Type | Episode | Expected | Actual | Fault flagged | First-attempt violations | Final unsupported claims | Citations valid | Time to brief (ms) | Build |
|---|---|---|---|---:|---:|---:|---|---:|---|
| Synthetic | Dust Storm Event | DUST | INCONCLUSIVE | false | 0 | 0 | true | 209 | PASS |
| Synthetic | Combustion / Traffic Event | COMBUSTION_TRAFFIC | COMBUSTION_TRAFFIC | false | 1 | 1 | true | 250 | FAIL |
| Synthetic | Biomass Burning Event | BIOMASS_BURNING | INCONCLUSIVE | false | 0 | 0 | true | 65 | PASS |
| Synthetic | Sensor Fault (STN-001) | SENSOR_FAULT | SENSOR_FAULT | true | 1 | 1 | true | 701 | FAIL |
| Synthetic | Ambiguous Episode (INCONCLUSIVE) | INCONCLUSIVE | INCONCLUSIVE | false | 0 | 0 | true | 337 | PASS |

# Limitations and required review

- **Heuristic screening is not validated science.** QC, anomaly thresholds, fingerprint rules, expected labels, and AQI breakpoints are prototype settings. They require domain-expert and official-source review against representative monitoring data.
- **Synthetic episodes are not real air-quality observations.** The seeded samples use generated values for pipeline testing only. Evaluation scores on them are not an accuracy or operational-readiness claim.
- **No causal attribution.** The system ranks broad source types only. It cannot establish that an event was caused by a particular source or entity, and it must not be used for legal or enforcement conclusions.
- **Real data needs human context.** Real CSVs must map to configured station IDs, use comparable pollutant units and timestamps, and preserve provenance. The importer does not manufacture expected labels; blank labels mean the episode is not scored for expected-vs-actual accuracy.
- **Provider availability controls freshness.** OpenAQ and Open-Meteo live polling are disabled in this prototype; replay data is the supported demo path. The AI integration retries provider failures, audits them, and marks the provider stale, but a generated template fallback is unvalidated and fails the evaluation build gate.
- **Weather and fire data can be absent.** Fingerprinting records this as missing evidence, which lowers certainty; absence is not evidence that wind or fire activity did not occur.
- **Official documents are not bundled.** The AI corpus starts empty. Team members must add verified, current official documents, complete the manifest metadata, and run ingestion before any source-backed citations can appear.
- **Retrieval and validation use heuristics.** Similarity thresholds, entity-name checks, action grounding, and the validator can have false positives or false negatives. Passing validation is not a substitute for human review.
- **Citizen guidance is incomplete.** Precaution content is an explicit placeholder, not health advice. The team must provide approved category-specific templates and official reporting-channel configuration.
- **Translations need review.** Hindi and Marathi UI text is draft text and is marked as needing native-speaker review.
- **Evaluation measures pipeline consistency, not truth.** It checks top-hypothesis agreement only for episodes with labels, fault flags, validator output, citation resolution, and time-to-brief. It does not evaluate causality, scientific validity, or field outcomes.
- **Full Compose execution depends on Docker.** The local H2 path is available for tests/demo work, but MySQL deployment and containerized execution must be verified in the target environment.

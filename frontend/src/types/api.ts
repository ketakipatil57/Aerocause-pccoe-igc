export type SourceMode = 'LIVE' | 'MOCK'

export interface ApiResult<T> {
  data: T
  source: SourceMode
}

export interface Station {
  id: number
  externalId: string
  name: string
  city: string
  lat: number
  lon: number
  provider: string
}

export interface Incident {
  id: number
  station: Station
  pollutant: string
  detectedAt: string
  windowStart: string
  windowEnd: string
  peakValue: number
  baselineValue: number
  status: string
  episodeId?: number
  synthetic: boolean
  topHypothesis?: Pick<Hypothesis, 'type' | 'score' | 'confidenceLabel'>
  qcFlags?: QcFlag[]
}

export interface Hypothesis {
  id: number
  type: string
  score: number
  confidenceLabel: 'LOW' | 'MEDIUM' | 'HIGH'
  evidenceForJson: string | string[]
  evidenceAgainstJson: string | string[]
  missingJson: string | string[]
  rank: number
}

export interface BriefContent {
  headline: string
  summary: string
  hypotheses: Array<{
    type: string
    explanation: string
    evidence_refs: string[]
    uncertainty: string
  }>
  verification_steps: string[]
  recommended_actions: Array<{ text: string; citation_ids: string[] }>
  unknowns: string[]
  disclaimer: string
}

export interface BriefRecord {
  id: number
  generatedAt: string
  contentJson: string
  validatorStatus: 'PASSED' | 'RETRIED' | 'TEMPLATE_FALLBACK'
  llmMode: 'LIVE' | 'MOCK'
}

export interface EvidenceItem {
  id: number
  type: string
  summary: string
  dataJson: string
  sourceRef: string
}

export interface QcFlag {
  id: number
  pollutant: string
  windowStart: string
  windowEnd: string
  rule: string
  severity: string
  detail: string
  createdAt: string
}

export interface Review {
  id: number
  reviewer: string
  decision: string
  comment: string | null
  createdAt: string
}

export interface IncidentDetail {
  incident: Incident
  hypotheses: Hypothesis[]
  evidenceItems: EvidenceItem[]
  qcFlags?: QcFlag[]
  brief: BriefRecord | null
  reviews: Review[]
  dataNotice: string | null
  decisionSupportNotice: string
}

export interface Reading {
  id: number
  pollutant: string
  value: number
  unit: string
  observedAt: string
  fetchedAt: string
  source: string
  qualityStatus: string
  synthetic: boolean
}

export interface AuditEvent {
  id: number
  incidentId: number | null
  eventType: string
  payloadJson: string
  createdAt: string
}

export interface Episode {
  id: number
  name: string
  description: string
  synthetic: boolean
  expectedTopHypothesis: string | null
  expectedFault: boolean | null
  durationHours: number
}

export interface ReplayStatus {
  running: boolean
  episodeId: number | null
  progress: number
  readingsProcessed: number
  incidentsCreated: number
  message?: string
  error?: string | null
}

export interface EvaluationResult {
  episodeId: number
  name: string
  synthetic: boolean
  expected: string | null
  actual: string
  confidence: string
  incidentCount: number
  expectedFault: boolean | null
  sensorFaultFlagged: boolean
  firstAttemptValidatorViolations: number
  unsupportedClaimsFinal: number
  validatorAvailable: boolean
  citationsValid: boolean
  timeToBriefMs: number | null
  pass: boolean | null
  buildPass: boolean
  buildFailure?: string
  dataNotice?: string
}

export interface Evaluation {
  runAt: string
  buildPass: boolean
  unsupportedClaimsFinalTotal: number
  validatorAvailable: boolean
  citationsValid: boolean
  episodes: EvaluationResult[]
  syntheticEpisodes: EvaluationResult[]
  realEpisodes: EvaluationResult[]
  summary: { total: number; pass: number; fail: number }
  dataNotice?: string
  decisionSupportNotice?: string
}

export interface Freshness {
  [key: string]: unknown
  dataNotice?: string
  replay?: { running: boolean; episodeId: number | string }
}

export interface CitizenAqi {
  stationName: string
  stationId: number
  aqi: number
  category: string
  dominantPollutant: string
  advice: string
  observedAt: string
  dataNotice: string
  disclaimer: string
}

export interface Passage {
  doc_key: string
  title: string
  publisher?: string
  version: string | null
  url: string | null
  passage_id: string
  excerpt: string
}

export interface CitizenSummary {
  summary: string
  precautions: string[]
  language: 'en' | 'hi' | 'mr'
  needs_native_speaker_review: boolean
}

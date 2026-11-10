import type {
  AuditEvent,
  BriefContent,
  BriefRecord,
  CitizenAqi,
  CitizenSummary,
  Episode,
  Evaluation,
  Incident,
  IncidentDetail,
  Passage,
  Reading,
  ReplayStatus,
  Review,
  Station,
} from '../types/api'

const now = Date.now()
const iso = (offsetMinutes: number) => new Date(now + offsetMinutes * 60_000).toISOString()

export const stations: Station[] = [
  { id: 1, externalId: 'STN-001', name: 'Central Monitor', city: 'Vayu Nagar', lat: 18.5204, lon: 73.8567, provider: 'SYNTHETIC' },
  { id: 2, externalId: 'STN-002', name: 'North Station', city: 'Vayu Nagar', lat: 18.55, lon: 73.84, provider: 'SYNTHETIC' },
  { id: 3, externalId: 'STN-003', name: 'East Station', city: 'Vayu Nagar', lat: 18.51, lon: 73.9, provider: 'SYNTHETIC' },
  { id: 4, externalId: 'STN-004', name: 'South Station', city: 'Vayu Nagar', lat: 18.49, lon: 73.86, provider: 'SYNTHETIC' },
]

export const incidents: Incident[] = [
  { id: 1, station: stations[0], pollutant: 'PM10', detectedAt: iso(-28), windowStart: iso(-180), windowEnd: iso(-10), peakValue: 182.4, baselineValue: 63.2, status: 'NEW', episodeId: 1, synthetic: true, qcFlags: [] },
  { id: 2, station: stations[1], pollutant: 'PM25', detectedAt: iso(-95), windowStart: iso(-240), windowEnd: iso(-70), peakValue: 96.8, baselineValue: 31.7, status: 'UNDER_REVIEW', episodeId: 5, synthetic: true, qcFlags: [{ id: 22, pollutant: 'PM25', windowStart: iso(-240), windowEnd: iso(-70), rule: 'ISOLATED_SPIKE', severity: 'WARNING', detail: 'Sample quality-control flag: reading pattern differs from nearby stations.', createdAt: iso(-90) }] },
  { id: 3, station: stations[2], pollutant: 'PM25', detectedAt: iso(-210), windowStart: iso(-360), windowEnd: iso(-190), peakValue: 121.6, baselineValue: 28.4, status: 'CONFIRMED', episodeId: 2, synthetic: true, qcFlags: [] },
]

const emptyPassages: Passage[] = []
const briefContent: BriefContent = {
  headline: 'Elevated particulate reading requires field verification',
  summary: 'The available measurements show a particulate increase above the recent baseline. The current evidence is inconclusive about the source, and no official source was found in the document corpus.',
  hypotheses: [{ type: 'INCONCLUSIVE', explanation: 'A particulate increase is present, but available evidence does not establish a source type.', evidence_refs: ['E-101'], uncertainty: 'The available evidence is limited and should be verified.' }],
  verification_steps: ['Check the monitor inlet and recent maintenance record.', 'Compare the time window with nearby station readings.', 'Collect a follow-up reading before drawing conclusions.'],
  recommended_actions: [],
  unknowns: ['No official guidance passage was available in the indexed corpus.'],
  disclaimer: 'Decision support only. Final verification and enforcement stay with authorised officials.',
}

const initialDetails: Record<number, IncidentDetail> = {
  1: {
    incident: incidents[0],
    hypotheses: [
      { id: 11, type: 'DUST', score: 0.72, confidenceLabel: 'HIGH', evidenceForJson: ['PM10 rose more strongly than PM2.5.', 'Wind speed increased during the event.'], evidenceAgainstJson: ['No upwind source observation is available.'], missingJson: ['Field inspection is not available.', 'Confirm conditions at neighbouring monitors.'], rank: 1 },
      { id: 12, type: 'COMBUSTION_TRAFFIC', score: 0.24, confidenceLabel: 'LOW', evidenceForJson: ['Particulate concentration increased.'], evidenceAgainstJson: ['NO2 and CO did not rise in the same window.'], missingJson: ['Traffic activity data is unavailable.'], rank: 2 },
      { id: 13, type: 'INCONCLUSIVE', score: 0.04, confidenceLabel: 'LOW', evidenceForJson: [], evidenceAgainstJson: [], missingJson: ['Independent field verification.'], rank: 3 },
    ],
    evidenceItems: [
      { id: 101, type: 'ANOMALY', summary: 'PM10 peaked at 182.4 µg/m³ against a 63.2 µg/m³ baseline.', dataJson: '{"peak":182.4,"baseline":63.2}', sourceRef: 'reading-window:1' },
      { id: 102, type: 'WEATHER', summary: 'Wind speed increased during the elevated reading window.', dataJson: '{"windSpeed":5.2,"direction":"NW"}', sourceRef: 'weather-window:1' },
      { id: 103, type: 'CROSS_POLLUTANT', summary: 'NO2 and CO did not show a corresponding increase.', dataJson: '{"pollutants":["NO2","CO"]}', sourceRef: 'reading-window:1' },
    ],
    brief: { id: 1, generatedAt: iso(-22), contentJson: JSON.stringify(briefContent), validatorStatus: 'PASSED', llmMode: 'MOCK' },
    reviews: [],
    dataNotice: 'SAMPLE DATA (synthetic)',
    decisionSupportNotice: 'Decision support only. Final decisions belong to qualified officers.',
  },
  2: {
    incident: incidents[1],
    hypotheses: [{ id: 21, type: 'INCONCLUSIVE', score: 0.46, confidenceLabel: 'LOW', evidenceForJson: ['Particulate concentration is above baseline.'], evidenceAgainstJson: ['Neighbouring station readings are incomplete.'], missingJson: ['Upwind station data.', 'Additional pollutant readings.'], rank: 1 }],
    evidenceItems: [{ id: 201, type: 'ANOMALY', summary: 'PM2.5 increased during the current window.', dataJson: '{"peak":96.8,"baseline":31.7}', sourceRef: 'reading-window:2' }],
    qcFlags: incidents[1].qcFlags,
    brief: null,
    reviews: [],
    dataNotice: 'SAMPLE DATA (synthetic)',
    decisionSupportNotice: 'Decision support only. Final decisions belong to qualified officers.',
  },
  3: {
    incident: incidents[2],
    hypotheses: [{ id: 31, type: 'DUST', score: 0.64, confidenceLabel: 'MEDIUM', evidenceForJson: ['A particulate increase was observed.'], evidenceAgainstJson: ['Wind data is incomplete.'], missingJson: ['Field verification.'], rank: 1 }],
    evidenceItems: [{ id: 301, type: 'ANOMALY', summary: 'PM2.5 increased above its recent baseline.', dataJson: '{"peak":121.6,"baseline":28.4}', sourceRef: 'reading-window:3' }],
    brief: null,
    reviews: [],
    dataNotice: 'SAMPLE DATA (synthetic)',
    decisionSupportNotice: 'Decision support only. Final decisions belong to qualified officers.',
  },
}

export const details = initialDetails
const initialDetailsSnapshot = structuredClone(initialDetails)
export const episodes: Episode[] = [
  { id: 1, name: 'Wind-driven dust event', description: 'Elevated particulates during stronger winds.', synthetic: true, expectedTopHypothesis: 'DUST', expectedFault: false, durationHours: 24 },
  { id: 2, name: 'Combustion plume', description: 'A mixed pollutant event for pipeline testing.', synthetic: true, expectedTopHypothesis: 'COMBUSTION_TRAFFIC', expectedFault: false, durationHours: 18 },
  { id: 3, name: 'Biomass burning pattern', description: 'A particulate event with an incomplete external signal.', synthetic: true, expectedTopHypothesis: 'BIOMASS_BURNING', expectedFault: false, durationHours: 20 },
  { id: 4, name: 'Isolated sensor spike', description: 'A single-station anomaly for quality-control checks.', synthetic: true, expectedTopHypothesis: 'SENSOR_FAULT', expectedFault: true, durationHours: 12 },
  { id: 5, name: 'Ambiguous mixed event', description: 'Conflicting indicators where an inconclusive result is valid.', synthetic: true, expectedTopHypothesis: 'INCONCLUSIVE', expectedFault: false, durationHours: 16 },
]

let replayStatus: ReplayStatus = { running: false, episodeId: null, progress: 0, readingsProcessed: 0, incidentsCreated: 0 }
let replayTimer: ReturnType<typeof setInterval> | undefined
let nextReviewId = 500
let nextBriefId = 20

export function resetMockState() {
  if (replayTimer) clearInterval(replayTimer)
  replayTimer = undefined
  Object.assign(details, structuredClone(initialDetailsSnapshot))
  incidents.forEach((incident, index) => {
    incident.status = initialDetailsSnapshot[index + 1]?.incident.status ?? incident.status
    details[incident.id].incident = incident
  })
  replayStatus = { running: false, episodeId: null, progress: 0, readingsProcessed: 0, incidentsCreated: 0 }
  nextReviewId = 500
  nextBriefId = 20
}

export function getIncidentDetail(id: number): IncidentDetail {
  const detail = details[id]
  if (!detail) throw new Error('Sample incident not found.')
  return detail
}

export function getReadings(incident: Incident): Reading[] {
  const count = 28
  return Array.from({ length: count }, (_, index) => {
    const progress = index / (count - 1)
    const pulse = Math.max(0, 1 - Math.abs(progress - 0.62) * 4.2)
    const baseline = incident.baselineValue + Math.sin(index * 0.64) * incident.baselineValue * 0.1
    return {
      id: index + 1,
      pollutant: incident.pollutant,
      value: Math.round((baseline + pulse * (incident.peakValue - incident.baselineValue)) * 10) / 10,
      unit: 'µg/m³',
      observedAt: new Date(new Date(incident.windowStart).getTime() + progress * (new Date(incident.windowEnd).getTime() - new Date(incident.windowStart).getTime())).toISOString(),
      fetchedAt: iso(-1),
      source: 'SYNTHETIC',
      qualityStatus: index === 17 ? 'SUSPECT' : 'GOOD',
      synthetic: true,
    }
  })
}

export function getAudit(id: number): AuditEvent[] {
  const detail = getIncidentDetail(id)
  const base: AuditEvent[] = [
    { id: id * 10 + 1, incidentId: id, eventType: 'INCIDENT_DETECTED', payloadJson: '{"source":"anomaly detection"}', createdAt: detail.incident.detectedAt },
    { id: id * 10 + 2, incidentId: id, eventType: 'QC_COMPLETED', payloadJson: '{"flags":1}', createdAt: iso(-26) },
    { id: id * 10 + 3, incidentId: id, eventType: 'HYPOTHESES_COMPUTED', payloadJson: '{"method":"rule based"}', createdAt: iso(-24) },
  ]
  if (detail.brief) base.push({ id: id * 10 + 4, incidentId: id, eventType: 'BRIEF_GENERATED', payloadJson: '{"mode":"MOCK"}', createdAt: detail.brief.generatedAt })
  return base.concat(detail.reviews.map((review) => ({
    id: review.id,
    incidentId: id,
    eventType: 'REVIEW_SUBMITTED',
    payloadJson: JSON.stringify({ reviewer: review.reviewer, decision: review.decision }),
    createdAt: review.createdAt,
  }))).sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

export function generateMockBrief(id: number): BriefRecord {
  const detail = getIncidentDetail(id)
  const top = detail.hypotheses[0]
  const content: BriefContent = {
    ...briefContent,
    hypotheses: [{
      type: top.type,
      explanation: `The code-ranked hypothesis is ${top.type}; available evidence should be checked before conclusions are drawn.`,
      evidence_refs: detail.evidenceItems.slice(0, 2).map((item) => `E-${item.id}`),
      uncertainty: 'This is a screening result, not a confirmed source attribution.',
    }],
  }
  const record: BriefRecord = { id: nextBriefId++, generatedAt: new Date().toISOString(), contentJson: JSON.stringify(content), validatorStatus: 'PASSED', llmMode: 'MOCK' }
  detail.brief = record
  return record
}

export function submitMockReview(id: number, reviewer: string, decision: string, comment: string): Review {
  const detail = getIncidentDetail(id)
  const review = { id: nextReviewId++, reviewer, decision, comment, createdAt: new Date().toISOString() }
  detail.reviews.unshift(review)
  detail.incident.status = decision === 'ACCEPT' ? 'CONFIRMED' : decision === 'REJECT' ? 'REJECTED' : 'UNDER_REVIEW'
  return review
}

export function getReplayStatus(): ReplayStatus {
  return { ...replayStatus }
}

export function startMockReplay(episodeId: number): ReplayStatus {
  if (replayTimer) clearInterval(replayTimer)
  replayStatus = { running: true, episodeId, progress: 0, readingsProcessed: 0, incidentsCreated: 0 }
  replayTimer = setInterval(() => {
    replayStatus.progress = Math.min(1, replayStatus.progress + 0.12)
    replayStatus.readingsProcessed += 24
    replayStatus.incidentsCreated = replayStatus.progress >= 0.6 ? 1 : 0
    if (replayStatus.progress >= 1) {
      replayStatus.running = false
      if (replayTimer) clearInterval(replayTimer)
    }
  }, 900)
  return getReplayStatus()
}

export function stopMockReplay(): ReplayStatus {
  if (replayTimer) clearInterval(replayTimer)
  replayStatus.running = false
  return getReplayStatus()
}

export function getMockEvaluation(): Evaluation {
  const rows = episodes.map((episode) => {
    const actual = episode.id === 1 ? 'DUST' : episode.id === 4 ? 'SENSOR_FAULT' : episode.id === 5 ? 'INCONCLUSIVE' : 'INCONCLUSIVE'
    return {
      episodeId: episode.id,
      name: episode.name,
      synthetic: true,
      expected: episode.expectedTopHypothesis,
      actual,
      confidence: 'MEDIUM',
      incidentCount: episode.id <= 3 ? 1 : 0,
      expectedFault: episode.expectedFault,
      sensorFaultFlagged: actual === 'SENSOR_FAULT',
      firstAttemptValidatorViolations: 0,
      unsupportedClaimsFinal: 0,
      validatorAvailable: true,
      citationsValid: true,
      timeToBriefMs: null,
      pass: actual === episode.expectedTopHypothesis,
      buildPass: true,
      dataNotice: 'SAMPLE DATA (synthetic)',
    }
  })
  return {
    runAt: new Date().toISOString(),
    episodes: rows,
    syntheticEpisodes: rows,
    realEpisodes: [],
    buildPass: true,
    unsupportedClaimsFinalTotal: 0,
    validatorAvailable: true,
    citationsValid: true,
    summary: { total: rows.length, pass: rows.filter((row) => row.pass).length, fail: rows.filter((row) => !row.pass).length },
    dataNotice: 'SAMPLE DATA (synthetic)',
    decisionSupportNotice: 'Pipeline test only; not an accuracy claim.',
  }
}

export function getMockAqi(stationId = 1): CitizenAqi {
  const station = stations.find((item) => item.id === stationId) ?? stations[0]
  return {
    stationName: station.name,
    stationId: station.id,
    aqi: 156,
    category: 'Poor',
    dominantPollutant: 'PM2.5',
    advice: 'Air quality is a sample reading. Check current official guidance.',
    observedAt: new Date().toISOString(),
    dataNotice: 'SAMPLE DATA (synthetic)',
    disclaimer: 'Decision support only. This is not an official government reading.',
  }
}

export function getMockCitizenSummary(aqi: CitizenAqi, language: 'en' | 'hi' | 'mr'): CitizenSummary {
  const text: Record<typeof language, string> = {
    en: `The AQI is ${aqi.aqi} (${aqi.category}). Available evidence may be linked to an uncertain pattern.`,
    hi: `वायु गुणवत्ता सूचकांक ${aqi.aqi} (${aqi.category}) है। उपलब्ध साक्ष्य का संबंध एक अनिश्चित स्वरूप से हो सकता है।`,
    mr: `वायु गुणवत्ता निर्देशांक ${aqi.aqi} (${aqi.category}) आहे. उपलब्ध पुराव्याचा संबंध एका अनिश्चित नमुन्याशी असू शकतो.`,
  }
  return { summary: text[language], precautions: [], language, needs_native_speaker_review: language !== 'en' }
}

export const mockPassages = emptyPassages

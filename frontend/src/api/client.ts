import * as mock from '../data/mock'
import type {
  ApiResult,
  AuditEvent,
  CitizenAqi,
  CitizenSummary,
  Episode,
  Evaluation,
  Freshness,
  Incident,
  IncidentDetail,
  Passage,
  Reading,
  ReplayStatus,
  Station,
} from '../types/api'

const apiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const aiBase = (import.meta.env.VITE_AI_API_BASE_URL || '/ai').replace(/\/$/, '')
const timeoutMs = Number(import.meta.env.VITE_API_TIMEOUT_MS || 5000)

export class ApiRequestError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiRequestError'
  }
}

async function request<T>(
  base: string,
  path: string,
  fallback: () => T,
  init?: RequestInit,
): Promise<ApiResult<T>> {
  let response: Response
  try {
    response = await fetch(`${base}${path}`, {
      ...init,
      signal: AbortSignal.timeout(timeoutMs),
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch (error) {
    if (error instanceof TypeError || (error instanceof DOMException && error.name === 'TimeoutError')) {
      return { data: fallback(), source: 'MOCK' }
    }
    throw error
  }
  if (!response.ok) {
    if ([502, 503, 504].includes(response.status)) return { data: fallback(), source: 'MOCK' }
    const body = await response.text()
    throw new ApiRequestError(response.status, `Request failed (${response.status}): ${body || response.statusText}`)
  }
  const body = await response.text()
  if (!body) return { data: fallback(), source: 'LIVE' }
  return { data: JSON.parse(body) as T, source: 'LIVE' }
}

const get = <T>(path: string, fallback: () => T, base = apiBase) =>
  request<T>(base, path, fallback)
const post = <T>(path: string, body: unknown, fallback: () => T, base = apiBase) =>
  request<T>(base, path, fallback, { method: 'POST', body: JSON.stringify(body) })

export const api = {
  incidents: () => get<Incident[]>('/incidents', () => mock.incidents),
  incident: (id: number) => get<IncidentDetail>(`/incidents/${id}`, () => mock.getIncidentDetail(id)),
  stations: () => get<Station[]>('/stations', () => mock.stations),
  readings: (incident: Incident, station = incident.station) => {
    const from = encodeURIComponent(incident.windowStart)
    const to = encodeURIComponent(incident.windowEnd)
    const pollutant = encodeURIComponent(incident.pollutant)
    return get<Reading[]>(`/stations/${station.id}/readings?from=${from}&to=${to}&pollutant=${pollutant}`, () => mock.getReadings({ ...incident, station }))
  },
  audit: (id: number) => get<AuditEvent[]>(`/incidents/${id}/audit`, () => mock.getAudit(id)),
  investigate: (id: number) => post<{ briefId?: number; validatorStatus?: string; llmMode?: string }>(
    `/incidents/${id}/investigate`,
    {},
    () => mock.generateMockBrief(id),
  ),
  review: (id: number, body: { reviewer: string; decision: string; comment: string }) =>
    post<{ reviewId?: number; decision: string; incidentStatus: string }>(
      `/incidents/${id}/review`,
      body,
      () => {
        const review = mock.submitMockReview(id, body.reviewer, body.decision, body.comment)
        return { reviewId: review.id, decision: review.decision, incidentStatus: mock.getIncidentDetail(id).incident.status }
      },
    ),
  episodes: () => get<Episode[]>('/episodes', () => mock.episodes),
  replayStatus: () => get<ReplayStatus>('/replay/status', mock.getReplayStatus),
  startReplay: (episodeId: number, speedMultiplier: number) => post<ReplayStatus>(
    '/replay/start',
    { episodeId, speedMultiplier },
    () => mock.startMockReplay(episodeId),
  ),
  importRealEpisodes: () => post<{ message: string; episodes: Array<{ id: number; name: string; synthetic: boolean }> }>(
    '/episodes/import-real',
    {},
    () => ({ message: 'No local real-data import is available in sample mode.', episodes: [] }),
  ),
  stopReplay: () => post<ReplayStatus>('/replay/stop', {}, mock.stopMockReplay),
  evaluation: () => get<Evaluation>('/evaluation/latest', mock.getMockEvaluation),
  runEvaluation: () => post<Evaluation>('/evaluation/run', {}, mock.getMockEvaluation),
  freshness: () => get<Freshness>('/health/sources', () => ({
    dataNotice: 'SAMPLE DATA (synthetic)',
    replay: { running: false, episodeId: 'none' },
  })),
  citizenAqi: (stationId: number) => get<CitizenAqi>(
    `/citizen/aqi?stationId=${stationId}`,
    () => mock.getMockAqi(stationId),
  ),
  citizenSummary: (aqi: CitizenAqi, language: 'en' | 'hi' | 'mr') => post<CitizenSummary>(
    '/citizen-summary',
    { aqi: aqi.aqi, category: aqi.category, evidence_summary: 'an uncertain particulate pattern', language, precaution_templates: [] },
    () => mock.getMockCitizenSummary(aqi, language),
    aiBase,
  ),
  retrieve: (query: string) => post<{ passages: Passage[] }>(
    '/retrieve',
    { query, top_k: 5 },
    () => ({ passages: [] }),
    aiBase,
  ),
}

export function formatError(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong while loading this information.'
}

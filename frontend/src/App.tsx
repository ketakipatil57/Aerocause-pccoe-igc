import { createContext, lazy, Suspense, useContext, useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity, AlertCircle, ArrowRight, ArrowUpRight, BookOpen, Check, CheckCircle2,
  ChevronDown, ChevronLeft, Clock3, CloudSun, FileCheck2, FileText, Gauge, Info, Leaf, ListChecks,
  LoaderCircle, Menu, Moon, Play, Radio, RefreshCw, ShieldCheck, Siren, Sun, TriangleAlert,
  Wind, X, XCircle,
} from 'lucide-react'
import { api, formatError } from './api/client'
import { stations } from './data/mock'
import citizenI18n from './data/citizen-i18n.json'
import citizenPrecautions from './data/citizen-precautions.json'
import type {
  ApiResult, AuditEvent, BriefContent, CitizenSummary, EvaluationResult, Hypothesis,
  Incident, IncidentDetail, Passage, SourceMode,
} from './types/api'
import './App.css'

const ReadingChart = lazy(() => import('./components/ReadingChart'))
const pollingMs = Number(import.meta.env.VITE_POLL_INTERVAL_MS || 10_000)
const reportUrl = import.meta.env.VITE_OFFICIAL_REPORT_URL || 'https://cpcb.nic.in/'
const DataModeContext = createContext<{ mode: SourceMode; setMode: (mode: SourceMode) => void }>({
  mode: 'LIVE',
  setMode: () => undefined,
})

function useSource<T>(result: ApiResult<T> | undefined) {
  const { setMode } = useContext(DataModeContext)
  useEffect(() => {
    if (result) setMode(result.source === 'MOCK' || isSyntheticData(result.data) ? 'MOCK' : 'LIVE')
  }, [result, setMode])
}

function isSyntheticData(value: unknown): boolean {
  if (Array.isArray(value)) return value.some(isSyntheticData)
  if (!isRecord(value)) return false
  if (value.synthetic === true) return true
  if (typeof value.dataNotice === 'string' && value.dataNotice.toLowerCase().includes('sample data')) return true
  return value.incident ? isSyntheticData(value.incident) : false
}

function useReplayStatus() {
  return useQuery({
    queryKey: ['replay-status'],
    queryFn: api.replayStatus,
    refetchInterval: (query) => query.state.data?.data.running ? pollingMs : false,
  })
}

function App() {
  const [mode, setMode] = useState<SourceMode>('LIVE')
  const value = useMemo(() => ({ mode, setMode }), [mode])
  return (
    <DataModeContext.Provider value={value}>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<IncidentDesk />} />
          <Route path="/incidents" element={<IncidentDesk />} />
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
          <Route path="/replay" element={<ReplayPage />} />
          <Route path="/evaluation" element={<EvaluationPage />} />
          <Route path="/citizen" element={<CitizenPage />} />
          <Route path="/sources" element={<SourcesPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </DataModeContext.Provider>
  )
}

function AppShell() {
  const { mode } = useContext(DataModeContext)
  const [dark, setDark] = useState(() => localStorage.getItem('aerocause-theme') === 'dark')
  const [menuOpen, setMenuOpen] = useState(false)
  const status = useReplayStatus()
  const freshness = useQuery({
    queryKey: ['freshness'],
    queryFn: api.freshness,
    refetchInterval: status.data?.data.running ? pollingMs : false,
  })
  useSource(status.data)
  useSource(freshness.data)
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    localStorage.setItem('aerocause-theme', dark ? 'dark' : 'light')
  }, [dark])
  const fetchedAt = freshness.dataUpdatedAt
  const stale = Boolean(fetchedAt && Date.now() - fetchedAt > 5 * 60_000)
  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="icon-button mobile-menu" aria-label="Toggle navigation" onClick={() => setMenuOpen(!menuOpen)}><Menu size={19} /></button>
        <Link className="brand" to="/incidents" aria-label="AeroCause incident desk">
          <span className="brand-mark"><Activity size={20} strokeWidth={2.2} /></span>
          <span><strong>AeroCause</strong><small>Air quality intelligence</small></span>
        </Link>
        <div className="topbar-right">
          <div className={`freshness ${stale ? 'is-stale' : ''}`} title="Live OpenAQ and weather polling are disabled in this prototype">
            <span className="freshness-dot" />
            <span className="freshness-copy"><strong>DATA FRESHNESS</strong><small>{mode === 'MOCK' ? 'Local sample fixture' : 'Replay / sources status'} · {fetchedAt ? formatRelative(fetchedAt) : 'checking'}</small></span>
            {stale && <TriangleAlert size={15} aria-label="Source status is stale" />}
          </div>
          {mode === 'MOCK' && <span className="sample-badge"><span className="sample-dot" /> SAMPLE DATA (synthetic)</span>}
          <button className="icon-button theme-toggle" aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'} onClick={() => setDark(!dark)}>
            {dark ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
      </header>
      <div className="notice-strip"><ShieldCheck size={14} /> Decision support only. Final verification and enforcement stay with authorised officials.</div>
      <div className="shell-body">
        <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
          <div className="sidebar-label">WORKSPACE</div>
          <nav aria-label="Main navigation">
            <NavItem to="/incidents" icon={<Siren size={17} />} label="Incidents" onNavigate={() => setMenuOpen(false)} />
            <NavItem to="/replay" icon={<Play size={17} />} label="Replay" onNavigate={() => setMenuOpen(false)} />
            <NavItem to="/evaluation" icon={<Activity size={17} />} label="Evaluation" onNavigate={() => setMenuOpen(false)} />
            <NavItem to="/citizen" icon={<Leaf size={17} />} label="Citizen View" onNavigate={() => setMenuOpen(false)} />
            <NavItem to="/sources" icon={<BookOpen size={17} />} label="Sources & Method" onNavigate={() => setMenuOpen(false)} />
          </nav>
          <div className="sidebar-foot">
            <div className="side-status"><span className="live-dot" /> {status.data?.data.running ? 'Replay in progress' : 'Replay is idle'}</div>
            <p>Evidence-led screening<br />for accountable response.</p>
            <span className="version-tag">PROTOTYPE · v0.3</span>
          </div>
        </aside>
        {menuOpen && <button className="mobile-backdrop" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
        <main className="main-content"><Outlet /></main>
      </div>
    </div>
  )
}

function NavItem({ to, icon, label, onNavigate }: { to: string; icon: React.ReactNode; label: string; onNavigate: () => void }) {
  return <NavLink to={to} onClick={onNavigate} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>{icon}<span>{label}</span></NavLink>
}

function PageTitle({ eyebrow, title, description, action }: { eyebrow: string; title: string; description?: string; action?: React.ReactNode }) {
  return <div className="page-title"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1>{description && <p>{description}</p>}</div>{action && <div className="page-title-action">{action}</div>}</div>
}

function StatusChip({ status }: { status: string }) {
  const key = status.toLowerCase().replaceAll('_', '-')
  return <span className={`status-chip status-${key}`}><span className="status-icon">{status === 'CONFIRMED' ? <CheckCircle2 size={13} /> : status === 'REJECTED' ? <XCircle size={13} /> : <Clock3 size={13} />}</span>{status.replaceAll('_', ' ')}</span>
}

function CategoryChip({ category }: { category: string }) {
  const className = category.toLowerCase().replaceAll(' ', '-')
  return <span className={`category-chip category-${className}`}><span className="category-symbol" aria-hidden="true">{category === 'Good' ? '●' : category === 'Severe' ? '◆' : '●'}</span>{category}</span>
}

function SampleNotice({ children = 'SAMPLE DATA (synthetic)' }: { children?: React.ReactNode }) {
  return <span className="sample-badge"><span className="sample-dot" />{children}</span>
}

function ErrorBanner({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <div className="error-banner" role="alert"><AlertCircle size={18} /><div><strong>We couldn’t load this view</strong><p>{message}</p></div>{onRetry && <button className="text-button" onClick={onRetry}>Try again</button>}</div>
}

function Skeleton({ rows = 3 }: { rows?: number }) {
  return <div className="skeleton-stack" aria-label="Loading"><span className="skeleton skeleton-title" />{Array.from({ length: rows }, (_, index) => <span className="skeleton" key={index} />)}</div>
}

function EmptyState({ icon, title, children, action }: { icon: React.ReactNode; title: string; children: React.ReactNode; action?: React.ReactNode }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><h3>{title}</h3><p>{children}</p>{action}</div>
}

function IncidentDesk() {
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [pollutantFilter, setPollutantFilter] = useState('ALL')
  const replay = useReplayStatus()
  const query = useQuery({
    queryKey: ['incidents', statusFilter],
    queryFn: api.incidents,
    refetchInterval: replay.data?.data.running ? pollingMs : false,
  })
  useSource(query.data)
  const data = query.data?.data ?? []
  const filtered = data.filter((incident) => (statusFilter === 'ALL' || incident.status === statusFilter) && (pollutantFilter === 'ALL' || incident.pollutant === pollutantFilter))
    .sort((a, b) => priorityOrder(a.status) - priorityOrder(b.status) || new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime())
  return (
    <div className="page-wrap">
      <PageTitle eyebrow="OPERATIONS · OVERVIEW" title="Incident desk" description="A clear view of detected air-quality events and their review state."
        action={<Link className="button button-primary" to="/replay"><Play size={15} /> Start a replay</Link>} />
      {query.isLoading ? <Skeleton rows={5} /> : query.isError ? <ErrorBanner message={formatError(query.error)} onRetry={() => void query.refetch()} /> : (
        <>
          <div className="summary-grid">
            <MetricCard label="Open incidents" value={String(data.filter((item) => item.status === 'NEW' || item.status === 'UNDER_REVIEW').length).padStart(2, '0')} detail="Awaiting review" icon={<Siren size={18} />} />
            <MetricCard label="Stations monitored" value="04" detail="Across Vayu Nagar" icon={<Radio size={18} />} />
            <MetricCard label="Replay status" value={replay.data?.data.running ? 'Active' : 'Idle'} detail={replay.data?.data.running ? `${Math.round(replay.data.data.progress * 100)}% complete` : 'No active episode'} icon={<Activity size={18} />} />
            <MetricCard label="Source coverage" value="Replay" detail="Live polling disabled" icon={<CloudSun size={18} />} />
          </div>
          <div className="section-heading incident-list-heading"><div><h2>Recent incidents</h2><p>{filtered.length} events · sorted by review priority and time</p></div>
            <div className="filter-row">
              <label className="sr-only" htmlFor="status-filter">Filter by status</label><select id="status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option><option value="NEW">New</option><option value="UNDER_REVIEW">Under review</option><option value="CONFIRMED">Confirmed</option><option value="REJECTED">Rejected</option></select>
              <label className="sr-only" htmlFor="pollutant-filter">Filter by pollutant</label><select id="pollutant-filter" value={pollutantFilter} onChange={(event) => setPollutantFilter(event.target.value)}><option value="ALL">All pollutants</option><option value="PM25">PM2.5</option><option value="PM10">PM10</option><option value="NO2">NO₂</option><option value="CO">CO</option></select>
            </div>
          </div>
          {filtered.length ? <div className="incident-grid">{filtered.map((incident) => <IncidentCard incident={incident} key={incident.id} />)}</div> :
            <EmptyState icon={<Siren size={21} />} title="No incidents match these filters">Try a different status or pollutant, or start a sample replay to create a new event.</EmptyState>}
          <div className="footnote"><Info size={14} /> QC labels indicate screening signals only; they are not a field diagnosis.</div>
        </>
      )}
    </div>
  )
}

function MetricCard({ label, value, detail, icon }: { label: string; value: string; detail: string; icon: React.ReactNode }) {
  return <div className="metric-card"><div className="metric-top"><span>{label}</span><span className="metric-icon">{icon}</span></div><strong>{value}</strong><small>{detail}</small></div>
}

function IncidentCard({ incident }: { incident: Incident }) {
  const delta = Math.round(((incident.peakValue - incident.baselineValue) / incident.baselineValue) * 100)
  const knownQcFlags = incident.qcFlags
  const likelyFault = knownQcFlags
    ? knownQcFlags.some((flag) => flag.rule === 'ISOLATED_SPIKE' || flag.rule === 'OUT_OF_RANGE')
    : incident.topHypothesis?.type === 'SENSOR_FAULT'
  const qcUnavailable = knownQcFlags === undefined && !incident.topHypothesis
  const qcLabel = qcUnavailable
    ? 'QC status unavailable'
    : likelyFault ? 'Suspected sensor fault' : 'Likely real event'
  return <Link to={`/incidents/${incident.id}`} className="incident-card">
    <div className="incident-card-top"><div className="station-id"><span className="station-glyph"><Gauge size={17} /></span><span><strong>{incident.station.name}</strong><small>{incident.station.city} · {formatDate(incident.detectedAt)}</small></span></div><ArrowUpRight size={17} className="card-arrow" /></div>
    <div className="incident-reading"><div><span className="reading-label">{displayPollutant(incident.pollutant)} PEAK</span><strong className="reading-value">{formatNumber(incident.peakValue)} <small>µg/m³</small></strong></div><div className="reading-delta"><ArrowUpRight size={14} />{delta}% <small>vs baseline</small></div></div>
    <div className="baseline-line"><span>Baseline</span><strong>{formatNumber(incident.baselineValue)} µg/m³</strong><span>Window {formatWindow(incident.windowStart, incident.windowEnd)}</span></div>
    <div className="card-footer"><StatusChip status={incident.status} /><span className={`qc-chip ${qcUnavailable ? 'qc-unknown' : likelyFault ? 'qc-fault' : 'qc-likely'}`}>{qcUnavailable ? <Info size={13} /> : likelyFault ? <TriangleAlert size={13} /> : <CheckCircle2 size={13} />}{qcLabel}</span></div>
    {incident.synthetic && <span className="card-sample"><span className="sample-dot" />Synthetic</span>}
  </Link>
}

function IncidentDetailPage() {
  const { id: idText } = useParams()
  const id = Number(idText)
  const location = useLocation()
  const navigate = useNavigate()
  const [tab, setTab] = useState('hypotheses')
  const [selectedPassage, setSelectedPassage] = useState<Passage | null>(null)
  const [compareStations, setCompareStations] = useState(false)
  const [decision, setDecision] = useState('ACCEPT')
  const [reviewer, setReviewer] = useState('')
  const [comment, setComment] = useState('')
  const queryClient = useQueryClient()
  const replay = useReplayStatus()
  const detailQuery = useQuery({
    queryKey: ['incident', id],
    queryFn: () => api.incident(id),
    enabled: Number.isFinite(id) && id > 0,
    refetchInterval: replay.data?.data.running ? pollingMs : false,
  })
  useSource(detailQuery.data)
  const detail = detailQuery.data?.data
  const readingsQuery = useQuery({ queryKey: ['readings', id], queryFn: () => api.readings(detail!.incident), enabled: Boolean(detail) })
  const stationsQuery = useQuery({ queryKey: ['stations'], queryFn: api.stations, enabled: compareStations })
  const nearbyStations = (stationsQuery.data?.data ?? []).filter((station) => station.id !== detail?.incident.station.id).slice(0, 3)
  const neighborReadingsQuery = useQuery({
    queryKey: ['neighbor-readings', id, nearbyStations.map((station) => station.id)],
    queryFn: async () => {
      const results = await Promise.all(nearbyStations.map(async (station) => ({
        station,
        result: await api.readings(detail!.incident, station),
      })))
      return {
        data: results.map(({ station, result }) => ({ station, readings: result.data })),
        source: results.some(({ result }) => result.source === 'MOCK') ? 'MOCK' as const : 'LIVE' as const,
      }
    },
    enabled: compareStations && Boolean(detail) && nearbyStations.length > 0,
  })
  const auditQuery = useQuery({ queryKey: ['audit', id], queryFn: () => api.audit(id), enabled: tab === 'audit' })
  useSource(readingsQuery.data)
  useSource(stationsQuery.data)
  useSource(neighborReadingsQuery.data)
  const investigate = useMutation({
    mutationFn: () => api.investigate(id),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['incident', id] }); await queryClient.invalidateQueries({ queryKey: ['audit', id] }) },
  })
  const review = useMutation({
    mutationFn: () => api.review(id, { reviewer: reviewer.trim() || 'Duty officer', decision, comment }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['incident', id] }),
        queryClient.invalidateQueries({ queryKey: ['audit', id] }),
        queryClient.invalidateQueries({ queryKey: ['incidents'] }),
      ])
      setComment('')
    },
  })
  const activeTab = new URLSearchParams(location.search).get('tab') || tab
  useEffect(() => {
    const urlTab = new URLSearchParams(location.search).get('tab')
    if (urlTab) setTab(urlTab)
  }, [location.search])
  if (detailQuery.isLoading) return <div className="page-wrap"><Skeleton rows={6} /></div>
  if (detailQuery.isError || !detail) return <div className="page-wrap"><ErrorBanner message={detailQuery.error ? formatError(detailQuery.error) : 'This incident could not be found.'} onRetry={() => void detailQuery.refetch()} /><Link className="button button-quiet" to="/incidents"><ChevronLeft size={16} /> Back to incidents</Link></div>
  const incident = detail.incident
  const category = peakCategory(incident)
  let brief: BriefContent | null = null
  let passages: Passage[] = []
  try {
    if (detail.brief) {
      const parsed: unknown = JSON.parse(detail.brief.contentJson)
      if (isRecord(parsed)) {
        brief = isBriefContent(parsed.brief) ? parsed.brief : isBriefContent(parsed) ? parsed : null
        if (Array.isArray(parsed.retrieved_passages)) passages = parsed.retrieved_passages.filter(isPassage)
      }
    }
  } catch { brief = null }
  const selectTab = (next: string) => { setTab(next); navigate(`/incidents/${id}?tab=${next}`, { replace: true }) }
  return (
    <div className="page-wrap detail-page">
      <div className="detail-back-row"><Link className="back-link" to="/incidents"><ChevronLeft size={16} /> Incident desk</Link><span className="detail-separator">/</span><span>Incident #{incident.id}</span></div>
      <div className="detail-hero">
        <div className="detail-hero-main">
          <div className="eyebrow"><span className="pulse-icon"><Activity size={13} /></span> INCIDENT · {String(incident.id).padStart(4, '0')}</div>
          <h1>{incident.station.name}<span className="hero-dot">·</span>{displayPollutant(incident.pollutant)}</h1>
          <div className="detail-meta"><span><Clock3 size={14} /> Detected {formatDate(incident.detectedAt)}</span><span><Gauge size={14} /> Peak {formatNumber(incident.peakValue)} µg/m³</span><span>Baseline {formatNumber(incident.baselineValue)} µg/m³</span></div>
        </div>
        <div className="detail-hero-side"><StatusChip status={incident.status} /><CategoryChip category={category} />{incident.synthetic && <SampleNotice />}</div>
      </div>
      <div className="chart-card">
        <div className="chart-heading"><div><h2>Reading profile</h2><p>{displayPollutant(incident.pollutant)} concentration during detection window · µg/m³</p></div><div className="chart-tools"><button className={`compare-toggle ${compareStations ? 'compare-active' : ''}`} aria-pressed={compareStations} onClick={() => setCompareStations(!compareStations)}><Activity size={13} />{compareStations ? 'Hide nearby' : 'Compare nearby'}</button><span className="chart-legend"><i className="legend-reading" />Observed <i className="legend-baseline" />Baseline <i className="legend-anomaly" />Anomaly window</span></div></div>
        {readingsQuery.isError ? <ErrorBanner message={formatError(readingsQuery.error)} onRetry={() => void readingsQuery.refetch()} /> : readingsQuery.isLoading ? <div className="chart-skeleton skeleton" /> :
          <Suspense fallback={<div className="chart-skeleton skeleton" />}><ReadingChart readings={readingsQuery.data?.data ?? []} incident={incident} neighbors={neighborReadingsQuery.data?.data ?? []} /></Suspense>}
        {compareStations && (stationsQuery.isError || neighborReadingsQuery.isError) && <div className="inline-error"><Info size={14} />Nearby readings are unavailable from the current API.</div>}
        <div className="wind-row"><span className="wind-title"><Wind size={15} /> WIND OBSERVATION</span>{incident.synthetic ? <span><b>↖</b> NW · 5.2 m/s</span> : <span className="table-muted">Wind observations are not exposed by this API.</span>}<span className="wind-caveat">{incident.synthetic ? 'Illustrative sample observation' : 'No wind data available'}</span></div>
      </div>
      <div className="footnote"><Info size={14} />Peak AQI category is estimated using the backend’s configured pollutant breakpoints; verify these settings against current official guidance.</div>
      <div className="detail-tabs" role="tablist" aria-label="Incident detail sections">
        {[
          ['hypotheses', 'Hypotheses', <Activity size={15} />],
          ['brief', 'Officer brief', <FileText size={15} />],
          ['evidence', 'Evidence & QC', <ShieldCheck size={15} />],
          ['audit', 'Timeline / Audit', <Clock3 size={15} />],
        ].map(([key, label, icon]) => <button key={key as string} role="tab" aria-selected={activeTab === key} className={activeTab === key ? 'selected' : ''} onClick={() => selectTab(key as string)}>{icon}{label}</button>)}
      </div>
      <div className="tab-content">
        {activeTab === 'hypotheses' && <HypothesesTab hypotheses={detail.hypotheses} />}
        {activeTab === 'brief' && <BriefTab detail={detail} brief={brief} passages={passages} onGenerate={() => investigate.mutate()} generating={investigate.isPending} onPassage={setSelectedPassage} error={investigate.error ? formatError(investigate.error) : undefined} />}
        {activeTab === 'evidence' && <EvidenceTab items={detail.evidenceItems} qcFlags={detail.qcFlags ?? []} />}
        {activeTab === 'audit' && <AuditTab query={auditQuery} />}
      </div>
      <ReviewBar decision={decision} setDecision={setDecision} reviewer={reviewer} setReviewer={setReviewer} comment={comment} setComment={setComment} onSubmit={() => review.mutate()} pending={review.isPending} error={review.error ? formatError(review.error) : undefined} />
      {selectedPassage && <CitationDrawer passage={selectedPassage} onClose={() => setSelectedPassage(null)} />}
    </div>
  )
}

function HypothesesTab({ hypotheses }: { hypotheses: Hypothesis[] }) {
  const inconclusive = hypotheses.some((item) => item.type === 'INCONCLUSIVE' && item.rank === 1)
  return <div className="tab-panel">
    {inconclusive && <div className="inconclusive-banner"><span className="inconclusive-icon"><Info size={19} /></span><div><strong>Inconclusive: here is what would help</strong><p>More independent observations can narrow the possibilities. The system will not force a source attribution when evidence is incomplete.</p></div></div>}
    <div className="section-heading"><div><h2>Ranked source-type hypotheses</h2><p>Rule-based screening · not an attribution to a specific entity</p></div><span className="small-muted">Ranked by evidence score</span></div>
    {hypotheses.length ? <div className="hypothesis-list">{hypotheses.map((hypothesis) => <HypothesisCard hypothesis={hypothesis} key={hypothesis.id} />)}</div> :
      <EmptyState icon={<Activity size={20} />} title="No hypotheses available">Hypotheses appear after the incident fingerprinting step has completed.</EmptyState>}
  </div>
}

function HypothesisCard({ hypothesis }: { hypothesis: Hypothesis }) {
  const evidenceFor = parseList(hypothesis.evidenceForJson)
  const evidenceAgainst = parseList(hypothesis.evidenceAgainstJson)
  const missing = parseList(hypothesis.missingJson)
  const percent = Math.max(0, Math.min(100, Math.round(hypothesis.score * 100)))
  return <article className={`hypothesis-card ${hypothesis.rank === 1 ? 'top-hypothesis' : ''}`}>
    <div className="hypothesis-head"><span className={`rank-badge ${hypothesis.rank === 1 ? 'rank-primary' : ''}`}>#{hypothesis.rank}</span><div className="hypothesis-name"><strong>{humanize(hypothesis.type)}</strong><small>Rule-based fingerprint</small></div><span className={`confidence confidence-${hypothesis.confidenceLabel.toLowerCase()}`}>{hypothesis.confidenceLabel} confidence</span><div className="score-box"><strong>{percent}%</strong><span>score</span></div></div>
    <div className="score-track"><span style={{ width: `${percent}%` }} /></div>
    <div className="evidence-columns">
      <EvidenceColumn title="Evidence FOR" kind="for" items={evidenceFor} />
      <EvidenceColumn title="Evidence AGAINST" kind="against" items={evidenceAgainst} />
      <EvidenceColumn title="MISSING evidence" kind="missing" items={missing} />
    </div>
  </article>
}

function EvidenceColumn({ title, kind, items }: { title: string; kind: string; items: string[] }) {
  return <div className={`evidence-column evidence-${kind}`}><h3>{kind === 'for' ? <CheckCircle2 size={14} /> : kind === 'against' ? <XCircle size={14} /> : <Info size={14} />}{title}</h3>
    {items.length ? <ul>{items.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul> : <p className="no-items">No items recorded</p>}</div>
}

function BriefTab({ detail, brief, passages, onGenerate, generating, onPassage, error }: { detail: IncidentDetail; brief: BriefContent | null; passages: Passage[]; onGenerate: () => void; generating: boolean; onPassage: (passage: Passage) => void; error?: string }) {
  const record = detail.brief
  const actions = brief?.recommended_actions ?? []
  if (record && !brief) return <div className="brief-empty panel"><div className="brief-empty-icon"><AlertCircle size={22} /></div><div><h2>Saved brief needs regeneration</h2><p>The saved response does not match the current brief format. Regenerate it before using the officer brief.</p>{error && <div className="inline-error"><AlertCircle size={15} />{error}</div>}<button className="button button-primary" onClick={onGenerate} disabled={generating}>{generating ? <LoaderCircle size={16} className="spin" /> : <RefreshCw size={15} />}{generating ? 'Generating brief…' : 'Regenerate brief'}</button></div></div>
  if (!brief) return <div className="brief-empty panel"><div className="brief-empty-icon"><FileCheck2 size={23} /></div><div><h2>No officer brief yet</h2><p>Generate an evidence-grounded brief from this incident package. It will be reviewed by the validator before it is saved.</p>{error && <div className="inline-error"><AlertCircle size={15} />{error}</div>}<button className="button button-primary" onClick={onGenerate} disabled={generating}>{generating ? <LoaderCircle size={16} className="spin" /> : <FileText size={16} />}{generating ? 'Generating brief…' : 'Generate brief'}</button></div></div>
  return <div className="tab-panel brief-panel">
    <div className="brief-status-row"><div className="brief-badges"><span className={`validator-badge validator-${record?.validatorStatus.toLowerCase()}`}><ShieldCheck size={13} />Validator · {humanize(record?.validatorStatus ?? 'UNKNOWN')}</span><span className="mode-badge"><Activity size={13} />LLM · {record?.llmMode ?? 'MOCK'}</span></div><span className="generated-at"><Clock3 size={13} /> Generated {formatDate(record?.generatedAt ?? '')}</span></div>
    {error && <ErrorBanner message={error} />}
    <article className="officer-brief panel">
      <div className="brief-kicker"><span className="brief-kicker-icon"><FileText size={15} /></span> OFFICER DECISION SUPPORT</div>
      <h2>{brief.headline}</h2><p className="brief-summary">{brief.summary}</p>
      {brief.hypotheses.length > 0 && <div className="brief-hypothesis"><span className="brief-section-label">HYPOTHESIS CONTEXT</span>{brief.hypotheses.map((item, index) => <p key={`${item.type}-${index}`}><strong>{humanize(item.type)}.</strong> {item.explanation} <em>{item.uncertainty}</em></p>)}</div>}
      <div className="brief-section"><div className="brief-section-title"><ListChecks size={16} /><h3>Verification steps</h3></div><ol className="verification-list">{brief.verification_steps.map((step, index) => <li key={`${step}-${index}`}><span className="step-number">{index + 1}</span><span>{step}</span><span className="generic-label">VERIFY</span></li>)}</ol></div>
      <div className="brief-section"><div className="brief-section-title"><BookOpen size={16} /><h3>Recommended actions</h3></div>
        {actions.length ? <div className="recommended-list">{actions.map((action, index) => <div className="recommended-item" key={`${action.text}-${index}`}><p>{action.text}</p><div className="citation-row">{action.citation_ids.length ? action.citation_ids.map((citationId) => {
          const passage = passages.find((item) => item.passage_id === citationId)
          return passage ? <button className="citation-chip" key={citationId} onClick={() => onPassage(passage)}><BookOpen size={12} />{passage.doc_key}<ArrowUpRight size={11} /></button> : <span className="no-source" key={citationId}>No official source found for this step</span>
        }) : <span className="no-source"><Info size={12} />No official source found for this step</span>}</div></div>)}</div> :
          <div className="no-citation-note"><Info size={15} /><span>No official source found for this step. No source-backed recommended actions are available from the current corpus.</span></div>}
      </div>
      {brief.unknowns.length > 0 && <div className="brief-unknowns"><strong>Known limitations</strong><ul>{brief.unknowns.map((unknown, index) => <li key={`${unknown}-${index}`}>{unknown}</li>)}</ul></div>}
      <div className="brief-disclaimer"><ShieldCheck size={15} />{brief.disclaimer}</div>
    </article>
    <button className="button button-quiet regenerate-button" onClick={onGenerate} disabled={generating}><RefreshCw size={14} /> Regenerate brief</button>
  </div>
}

function CitationDrawer({ passage, onClose }: { passage: Passage; onClose: () => void }) {
  return <div className="drawer-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <aside className="citation-drawer" role="dialog" aria-modal="true" aria-labelledby="citation-title">
      <div className="drawer-header"><div><span className="eyebrow">OFFICIAL SOURCE PASSAGE</span><h2 id="citation-title">{passage.title}</h2></div><button className="icon-button" aria-label="Close citation details" onClick={onClose}><X size={18} /></button></div>
      <div className="drawer-meta"><span>{passage.publisher || passage.doc_key}</span><span>Version {passage.version}</span><span>Passage {passage.passage_id}</span></div>
      <div className="drawer-excerpt"><span className="brief-section-label">RETRIEVED EXCERPT</span><p>{passage.excerpt}</p></div>
      {passage.url && <a className="button button-secondary drawer-link" href={passage.url} target="_blank" rel="noreferrer">Open source document <ArrowUpRight size={15} /></a>}
      <p className="drawer-caveat"><Info size={14} />Confirm the current version and applicability with the source publisher.</p>
    </aside>
  </div>
}

function EvidenceTab({ items, qcFlags }: { items: IncidentDetail['evidenceItems']; qcFlags: NonNullable<IncidentDetail['qcFlags']> }) {
  return <div className="tab-panel">
    <div className="section-heading"><div><h2>Evidence package</h2><p>Structured inputs behind the computed hypotheses and generated brief</p></div><span className="evidence-count">{items.length} evidence items</span></div>
    {items.length ? <div className="table-card"><table><thead><tr><th>Evidence</th><th>Type</th><th>Finding</th><th>Source reference</th></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td className="mono">E-{item.id}</td><td><span className="type-chip">{humanize(item.type)}</span></td><td>{item.summary}</td><td className="table-muted">{item.sourceRef}</td></tr>)}</tbody></table></div> : <EmptyState icon={<FileText size={19} />} title="Evidence package is empty">No evidence items have been attached to this incident yet.</EmptyState>}
    <div className="section-heading qc-heading"><div><h2>Quality-control flags</h2><p>Automated checks that inform data confidence</p></div></div>
    {qcFlags.length ? <div className="qc-flag-list">{qcFlags.map((flag) => <div className="qc-flag-row" key={flag.id}><span className="qc-flag-icon"><TriangleAlert size={16} /></span><div><strong>{humanize(flag.rule)} · {displayPollutant(flag.pollutant)}</strong><p>{flag.detail} · {formatWindow(flag.windowStart, flag.windowEnd)}</p></div><span className="severity-chip">{flag.severity}</span></div>)}</div> :
      <div className="no-flags"><CheckCircle2 size={17} /><span>No QC flags are attached to this sample incident.</span></div>}
    <p className="footnote"><Info size={14} />QC outcomes flag data for review; they do not prove a pollution source.</p>
  </div>
}

function AuditTab({ query }: { query: ReturnType<typeof useQuery<ApiResult<AuditEvent[]>>> }) {
  if (query.isLoading) return <Skeleton rows={4} />
  if (query.isError) return <ErrorBanner message={formatError(query.error)} onRetry={() => void query.refetch()} />
  const events = query.data?.data ?? []
  return <div className="tab-panel">
    <div className="section-heading"><div><h2>Timeline & audit trail</h2><p>Recorded processing and review events for this incident</p></div></div>
    {events.length ? <ol className="audit-timeline">{events.map((event) => <li key={event.id}><span className={`audit-point ${event.eventType.includes('REVIEW') ? 'audit-review' : ''}`}><AuditIcon event={event.eventType} /></span><div className="audit-card"><div className="audit-card-head"><strong>{humanize(event.eventType)}</strong><time>{formatDate(event.createdAt)}</time></div><p>{auditDescription(event)}</p></div></li>)}</ol> :
      <EmptyState icon={<Clock3 size={20} />} title="No audit events recorded">Events will appear here as the incident is processed and reviewed.</EmptyState>}
  </div>
}

function AuditIcon({ event }: { event: string }) {
  if (event.includes('REVIEW')) return <Check size={14} />
  if (event.includes('QC')) return <ShieldCheck size={14} />
  if (event.includes('BRIEF')) return <FileText size={14} />
  return <Activity size={14} />
}

function ReviewBar({ decision, setDecision, reviewer, setReviewer, comment, setComment, onSubmit, pending, error }: { decision: string; setDecision: (value: string) => void; reviewer: string; setReviewer: (value: string) => void; comment: string; setComment: (value: string) => void; onSubmit: () => void; pending: boolean; error?: string }) {
  return <section className="review-bar" aria-label="Officer review">
    <div className="review-intro"><span className="review-icon"><FileCheck2 size={17} /></span><div><strong>Officer review</strong><small>Record a decision to update status and audit trail.</small></div></div>
    <label className="sr-only" htmlFor="review-decision">Review decision</label><select id="review-decision" value={decision} onChange={(event) => setDecision(event.target.value)}><option value="ACCEPT">Accept</option><option value="REJECT">Reject</option><option value="NEEDS_MORE_EVIDENCE">Needs more evidence</option></select>
    <label className="sr-only" htmlFor="reviewer-name">Reviewer name</label><input id="reviewer-name" className="reviewer-input" placeholder="Officer name" value={reviewer} onChange={(event) => setReviewer(event.target.value)} />
    <label className="sr-only" htmlFor="review-comment">Review comment</label><input id="review-comment" className="comment-input" placeholder="Add a review note (optional)" value={comment} onChange={(event) => setComment(event.target.value)} />
    <button className="button button-primary review-submit" onClick={onSubmit} disabled={pending}>{pending ? <LoaderCircle className="spin" size={15} /> : <Check size={15} />}{pending ? 'Saving…' : 'Save review'}</button>
    {error && <span className="review-error" role="alert">{error}</span>}
  </section>
}

function ReplayPage() {
  const queryClient = useQueryClient()
  const episodeQuery = useQuery({ queryKey: ['episodes'], queryFn: api.episodes })
  const replayQuery = useReplayStatus()
  useSource(episodeQuery.data)
  useSource(replayQuery.data)
  const [episodeId, setEpisodeId] = useState<number>(1)
  const [speed, setSpeed] = useState(60)
  const start = useMutation({ mutationFn: () => api.startReplay(episodeId, speed), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['replay-status'] }) })
  const stop = useMutation({ mutationFn: api.stopReplay, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['replay-status'] }) })
  const incidentsQuery = useQuery({
    queryKey: ['incidents', 'replay'],
    queryFn: api.incidents,
    refetchInterval: replayQuery.data?.data.running ? pollingMs : false,
  })
  const status = replayQuery.data?.data
  const episodes = episodeQuery.data?.data ?? []
  const replayIncidents = incidentsQuery.data?.data ?? []
  const currentEpisode = episodes.find((episode) => episode.id === episodeId)
  if (episodeQuery.isLoading) return <div className="page-wrap"><Skeleton rows={5} /></div>
  if (episodeQuery.isError) return <div className="page-wrap"><ErrorBanner message={formatError(episodeQuery.error)} onRetry={() => void episodeQuery.refetch()} /></div>
  return <div className="page-wrap">
    <PageTitle eyebrow="OPERATIONS · REPLAY" title="Episode replay" description="Run a synthetic episode through the same quality and incident pipeline." />
    <div className="replay-layout">
      <section className="replay-control panel">
        <div className="panel-title"><span className="panel-icon"><Play size={17} /></span><div><h2>Configure replay</h2><p>Choose an episode and playback speed.</p></div></div>
        <label htmlFor="episode-select">Episode</label>
        <select id="episode-select" value={episodeId} onChange={(event) => setEpisodeId(Number(event.target.value))} disabled={Boolean(status?.running)}>{episodes.map((episode) => <option key={episode.id} value={episode.id}>{episode.name}</option>)}</select>
        {currentEpisode && <div className="episode-description"><p>{currentEpisode.description}</p><span>Expected top hypothesis · <strong>{humanize(currentEpisode.expectedTopHypothesis)}</strong></span></div>}
        <label htmlFor="speed-select">Playback speed</label>
        <select id="speed-select" value={speed} onChange={(event) => setSpeed(Number(event.target.value))} disabled={Boolean(status?.running)}><option value={1}>1× · real time</option><option value={60}>60× · demo</option><option value={120}>120× · fast demo</option></select>
        {currentEpisode?.synthetic && <div className="replay-synthetic"><SampleNotice /><span>Sample episode; not live environmental data.</span></div>}
        <div className="replay-controls">
          <button className="button button-primary" onClick={() => start.mutate()} disabled={Boolean(status?.running) || start.isPending || !currentEpisode}>{start.isPending ? <LoaderCircle className="spin" size={16} /> : <Play size={15} />}{status?.running ? 'Replay running' : 'Start replay'}</button>
          <button className="button button-secondary" onClick={() => stop.mutate()} disabled={!status?.running || stop.isPending}><X size={15} />Stop</button>
        </div>
        {(start.error || stop.error) && <div className="inline-error"><AlertCircle size={15} />{formatError(start.error ?? stop.error)}</div>}
      </section>
      <section className="replay-monitor panel">
        <div className="panel-title"><span className="panel-icon"><Radio size={17} /></span><div><h2>Replay monitor</h2><p>Live processing status and created incidents</p></div><span className={`replay-state ${status?.running ? 'state-running' : ''}`}><span />{status?.running ? 'IN PROGRESS' : 'IDLE'}</span></div>
        {replayQuery.isError ? <ErrorBanner message={formatError(replayQuery.error)} onRetry={() => void replayQuery.refetch()} /> : <>
          <div className="progress-overview"><div><span>PROGRESS</span><strong>{Math.round((status?.progress ?? 0) * 100)}<small>%</small></strong></div><div><span>READINGS PROCESSED</span><strong>{status?.readingsProcessed ?? 0}</strong></div><div><span>INCIDENTS CREATED</span><strong>{status?.incidentsCreated ?? 0}</strong></div></div>
          <div className="progress-track"><span style={{ width: `${(status?.progress ?? 0) * 100}%` }} /></div>
          <div className="monitor-note"><Info size={15} /><span>{status?.running ? `Episode ${status.episodeId} is being fed through ingestion, QC, anomaly detection and fingerprinting.` : 'Start a replay to see pipeline progress here. Status updates every 10 seconds while a replay is running.'}</span></div>
        </>}
        <div className="replay-events-heading"><h3>Recent incidents</h3><span>{replayIncidents.length} available</span></div>
        {incidentsQuery.isError ? <ErrorBanner message={formatError(incidentsQuery.error)} onRetry={() => void incidentsQuery.refetch()} /> :
          replayIncidents.length ? <div className="replay-incident-list">{replayIncidents.slice(0, 4).map((incident) => <Link className="replay-incident-row" key={incident.id} to={`/incidents/${incident.id}`}><span className="replay-row-glyph"><Activity size={15} /></span><span><strong>{incident.station.name} · {displayPollutant(incident.pollutant)}</strong><small>{formatDate(incident.detectedAt)} · {humanize(incident.status)}</small></span><ArrowRight size={15} /></Link>)}</div> :
            <EmptyState icon={<Siren size={19} />} title="No incidents yet">Incidents will appear as the replay advances through the episode.</EmptyState>}
      </section>
    </div>
    <div className="footnote"><ShieldCheck size={14} />Replay data is synthetic and for pipeline testing only.</div>
  </div>
}

function EvaluationPage() {
  const queryClient = useQueryClient()
  const replay = useReplayStatus()
  const resultQuery = useQuery({
    queryKey: ['evaluation'],
    queryFn: api.evaluation,
    refetchInterval: replay.data?.data.running ? pollingMs : false,
  })
  const run = useMutation({ mutationFn: api.runEvaluation, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['evaluation'] }) })
  useSource(resultQuery.data)
  if (resultQuery.isLoading) return <div className="page-wrap"><Skeleton rows={5} /></div>
  if (resultQuery.isError) return <div className="page-wrap"><ErrorBanner message={formatError(resultQuery.error)} onRetry={() => void resultQuery.refetch()} /></div>
  const result = resultQuery.data?.data
  const rows = result?.episodes ?? []
  return <div className="page-wrap">
    <PageTitle eyebrow="QUALITY · EVALUATION" title="Pipeline evaluation" description="Compare configured expected outcomes with the latest incident hypotheses." action={<button className="button button-primary" onClick={() => run.mutate()} disabled={run.isPending}>{run.isPending ? <LoaderCircle size={15} className="spin" /> : <RefreshCw size={15} />}{run.isPending ? 'Starting…' : 'Run evaluation'}</button>} />
    <div className="evaluation-disclaimer"><TriangleAlert size={17} /><p><strong>Results on synthetic sample episodes are a pipeline test, not an accuracy claim.</strong><span>Evaluation uses seeded episodes and may report “NONE” until an episode has been replayed.</span></p><SampleNotice /></div>
    {run.error && <ErrorBanner message={formatError(run.error)} />}
    {run.data?.data.message && <div className="run-message"><Info size={15} />{run.data.data.message}</div>}
    {result && <div className="summary-grid eval-metrics"><MetricCard label="Episodes evaluated" value={String(result.summary.total).padStart(2, '0')} detail="Configured sample set" icon={<Activity size={18} />} /><MetricCard label="Expected match" value={String(result.summary.pass).padStart(2, '0')} detail="Matching top hypothesis" icon={<CheckCircle2 size={18} />} /><MetricCard label="Needs inspection" value={String(result.summary.fail).padStart(2, '0')} detail="Mismatch or not replayed" icon={<TriangleAlert size={18} />} /><MetricCard label="Last updated" value={result.runAt ? formatTime(result.runAt) : '—'} detail="Latest backend result" icon={<Clock3 size={18} />} /></div>}
    <div className="section-heading eval-table-heading"><div><h2>Episode results</h2><p>Each row represents one configured replay episode.</p></div></div>
    {rows.length ? <div className="table-card eval-table"><table><thead><tr><th>Episode</th><th>Expected hypothesis</th><th>Actual top hypothesis</th><th>Fault detected</th><th>Unsupported claims</th><th>Citation validity</th><th>Validator</th><th>Result</th></tr></thead><tbody>{rows.map((row) => <EvaluationRow key={row.episodeId} row={row} />)}</tbody></table></div> :
      <EmptyState icon={<Activity size={20} />} title="No evaluation results yet">Start a sample episode replay, then refresh results here.</EmptyState>}
    <div className="footnote"><Info size={14} />Unsupported-claim counts, citation validity and time-to-brief are not provided by the current evaluation API; they are intentionally shown as unavailable rather than inferred.</div>
  </div>
}

function EvaluationRow({ row }: { row: EvaluationResult }) {
  return <tr><td><strong>{row.name}</strong><small className="table-subline">Episode {row.episodeId} · synthetic</small></td><td>{humanize(row.expected)}</td><td>{row.actual === 'NONE' ? <span className="no-result">Not replayed</span> : humanize(row.actual)}</td><td><span className="not-reported">{row.actual === 'SENSOR_FAULT' ? 'Flagged by rule' : row.actual === 'NONE' ? '—' : 'Not reported'}</span></td><td><span className="not-reported">Not reported</span></td><td><span className="not-reported">Not reported</span></td><td><span className="not-reported">Not reported</span></td><td><span className={`result-chip ${row.pass ? 'result-pass' : 'result-fail'}`}>{row.pass ? <CheckCircle2 size={13} /> : <TriangleAlert size={13} />}{row.pass ? 'Match' : 'Review'}</span></td></tr>
}

function CitizenPage() {
  const [stationId, setStationId] = useState(1)
  const [language, setLanguage] = useState<'en' | 'hi' | 'mr'>('en')
  const replay = useReplayStatus()
  const aqiQuery = useQuery({
    queryKey: ['citizen-aqi', stationId],
    queryFn: () => api.citizenAqi(stationId),
    refetchInterval: replay.data?.data.running ? pollingMs : false,
  })
  const aqi = aqiQuery.data?.data
  const summaryQuery = useQuery({ queryKey: ['citizen-summary', aqi?.aqi, language], queryFn: () => api.citizenSummary(aqi!, language), enabled: Boolean(aqi) })
  useSource(aqiQuery.data)
  if (aqiQuery.isLoading) return <div className="page-wrap"><Skeleton rows={5} /></div>
  if (aqiQuery.isError || !aqi) return <div className="page-wrap"><ErrorBanner message={aqiQuery.error ? formatError(aqiQuery.error) : 'Air quality is unavailable.'} onRetry={() => void aqiQuery.refetch()} /></div>
  const summary: CitizenSummary | undefined = summaryQuery.data?.data
  const labels = citizenI18n[language]
  const precautionText = citizenPrecautions.categories[aqi.category as keyof typeof citizenPrecautions.categories] ?? 'Template placeholder — replace with reviewed official guidance.'
  return <div className="page-wrap citizen-page">
    <PageTitle eyebrow="PUBLIC INFORMATION · CITIZEN VIEW" title="Air quality, clearly explained" description="A plain-language view of the latest available sample reading." action={<SampleNotice />} />
    <div className="citizen-toolbar"><label htmlFor="citizen-station">{labels.station}</label><div className="select-with-icon"><Gauge size={16} /><select id="citizen-station" value={stationId} onChange={(event) => setStationId(Number(event.target.value))}>{stations.map((station) => <option key={station.id} value={station.id}>{station.name} · {station.city}</option>)}</select><ChevronDown size={14} /></div><div className="language-switch" role="group" aria-label="Choose language">{([['en', 'English'], ['hi', 'हिन्दी'], ['mr', 'मराठी']] as const).map(([code, name]) => <button key={code} className={language === code ? 'language-active' : ''} onClick={() => setLanguage(code)}>{name}</button>)}</div></div>
    <div className="citizen-hero panel">
      <div className={`aqi-disc aqi-${aqi.category.toLowerCase().replaceAll(' ', '-')}`}><span>AQI</span><strong>{aqi.aqi}</strong><small>{aqi.category}</small></div>
      <div className="citizen-hero-copy"><span className="eyebrow">{labels.currentReading} · {displayPollutant(aqi.dominantPollutant)}</span><h2>{citizenMeaning(aqi.category)}</h2><p>{aqi.advice}</p><div className="aqi-station-line"><Gauge size={14} />{aqi.stationName}<span className="dot-separator">·</span><Clock3 size={14} />{formatDate(aqi.observedAt)}</div><p className="citizen-disclaimer">{aqi.disclaimer}</p></div>
    </div>
    {summaryQuery.isError && <div className="inline-error"><Info size={15} />Using a built-in plain-language template because the summary service is unavailable.</div>}
    <div className="citizen-info-grid">
      <section className="citizen-info-card panel"><div className="citizen-card-head"><span className="citizen-card-icon why-icon"><Wind size={17} /></span><div><h3>{labels.whyHigh}</h3><small>{labels.uncertainCause}</small></div></div><p className="citizen-summary-text">{summary?.summary ?? 'A summary is being prepared from available evidence.'}</p>{(summary?.needs_native_speaker_review || language !== 'en') && <span className="native-review"><Info size={12} />Needs native-speaker review · {labels.needsReview}</span>}</section>
      <section className="citizen-info-card panel"><div className="citizen-card-head"><span className="citizen-card-icon precaution-icon"><ShieldCheck size={17} /></span><div><h3>{labels.precautions}</h3><small>{labels.precautionPlaceholder}</small></div></div><div className="placeholder-guidance"><span className="placeholder-tag">PLACEHOLDER · NOT HEALTH ADVICE</span><p>{precautionText}</p><small>The project team must replace this placeholder with reviewed official guidance before public use.</small></div></section>
    </div>
    <section className="report-card panel"><div className="report-icon"><Radio size={18} /></div><div><h3>{labels.report}</h3><p>For official reporting channels and current public guidance, use the configured government portal.</p></div><a className="button button-secondary" href={reportUrl} target="_blank" rel="noreferrer">{labels.officialPortal} <ArrowUpRight size={15} /></a></section>
    <div className="citizen-bottom-note"><ShieldCheck size={15} />This prototype is not an official government AQI service. Verify current conditions with the relevant authority.</div>
  </div>
}

function SourcesPage() {
  const freshness = useQuery({ queryKey: ['freshness'], queryFn: api.freshness })
  const retrieved = useQuery({ queryKey: ['sources-check'], queryFn: () => api.retrieve('official guidance documents'), retry: false })
  useSource(freshness.data)
  return <div className="page-wrap">
    <PageTitle eyebrow="TRANSPARENCY · METHOD" title="Sources & method" description="What AeroCause uses, how it reasons, and where its limits are." />
    {freshness.isError && <ErrorBanner message={formatError(freshness.error)} onRetry={() => void freshness.refetch()} />}
    <div className="method-grid">
      <MethodCard icon={<Radio size={18} />} title="Air-quality readings" tag="REPLAY FIRST"><p>Readings are ingested from configured providers or synthetic episode replays. In this prototype, live OpenAQ polling is disabled; the current dashboard uses sample data when the backend is offline.</p></MethodCard>
      <MethodCard icon={<ShieldCheck size={18} />} title="Quality checks" tag="RULE-BASED"><p>Automated checks mark suspicious patterns such as isolated spikes or stale observations. A QC flag is a prompt for verification, not proof of a sensor fault.</p></MethodCard>
      <MethodCard icon={<Activity size={18} />} title="Hypothesis ranking" tag="DETERMINISTIC"><p>Code ranks source-type hypotheses from the evidence package. INCONCLUSIVE is a valid result. The system does not name facilities, companies, roads or people.</p></MethodCard>
      <MethodCard icon={<BookOpen size={18} />} title="Official references" tag="USER-MANAGED RAG"><p>The AI service can retrieve passages only from documents the team adds to its corpus manifest. No official document text is bundled in this prototype.</p></MethodCard>
    </div>
    <section className="panel sources-panel"><div className="panel-title"><span className="panel-icon"><FileText size={17} /></span><div><h2>Documents available to retrieval</h2><p>Only passages from indexed sources can support citations.</p></div><span className={`source-state ${retrieved.isError ? 'source-unavailable' : ''}`}>{retrieved.isLoading ? 'CHECKING' : retrieved.isError ? 'NOT CONNECTED' : `${retrieved.data?.data.passages.length ?? 0} PASSAGES`}</span></div>
      {retrieved.isError ? <div className="sources-empty"><Info size={17} /><div><strong>AI retrieval service is unavailable.</strong><p>Document metadata is not exposed by the current API. Check the AI service and its manifest directly before relying on any references.</p></div></div> :
        retrieved.data?.data.passages.length ? <div className="source-list">{retrieved.data.data.passages.map((passage) => <div className="source-row" key={passage.passage_id}><BookOpen size={16} /><span><strong>{passage.title}</strong><small>{passage.publisher} · Version {passage.version} · {passage.doc_key}</small></span>{passage.url && <a href={passage.url} target="_blank" rel="noreferrer" aria-label={`Open ${passage.title}`}><ArrowUpRight size={15} /></a>}</div>)}</div> :
          <div className="sources-empty"><Info size={17} /><div><strong>No official source passages found.</strong><p>The corpus is empty or no documents matched this query. Add reviewed official documents and a metadata manifest to the AI service corpus.</p><Link to="/incidents/1?tab=brief">See how briefs handle missing citations <ArrowRight size={13} /></Link></div></div>}
    </section>
    <section className="panel freshness-panel"><div className="panel-title"><span className="panel-icon"><CloudSun size={17} /></span><div><h2>Source freshness</h2><p>Live providers are disabled by default; replay is the supported demo source.</p></div></div>
      <div className="freshness-sources">{['OpenAQ · Live polling disabled', 'Open-Meteo · Live polling disabled', 'NASA FIRMS · Disabled by default'].map((source) => <div className="freshness-source-row" key={source}><span className="source-off-dot" /><span>{source}</span><span className="disabled-label">NOT CONNECTED</span></div>)}</div>
    </section>
    <div className="limitations-note"><TriangleAlert size={17} /><p><strong>Known limitations</strong><span>Proper-noun checks and action-to-citation matching are heuristics. AQI or source hypotheses are not an enforcement decision. Human review and current official guidance are required.</span></p></div>
  </div>
}

function MethodCard({ icon, title, tag, children }: { icon: React.ReactNode; title: string; tag: string; children: React.ReactNode }) {
  return <article className="method-card panel"><div className="method-card-top"><span className="panel-icon">{icon}</span><span className="method-tag">{tag}</span></div><h3>{title}</h3>{children}</article>
}

function NotFound() {
  return <div className="page-wrap"><EmptyState icon={<AlertCircle size={20} />} title="Page not found">That page is not part of the AeroCause workspace.</EmptyState><Link className="button button-primary" to="/incidents">Return to incident desk</Link></div>
}

function priorityOrder(status: string) {
  return ({ NEW: 0, UNDER_REVIEW: 1, INCONCLUSIVE: 2, CONFIRMED: 3, REJECTED: 4 }[status] ?? 5)
}

function parseList(value: string | string[]): string[] {
  if (Array.isArray(value)) return value
  try {
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.filter((item): item is string => typeof item === 'string')
  } catch { /* Older records may contain plain text rather than JSON. */ }
  return value ? [value] : []
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 1 }).format(value)
}
function formatDate(value: string) {
  if (!value) return 'Time unavailable'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function formatTime(value: string) {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
function formatRelative(timestamp: number) {
  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000))
  return seconds < 10 ? 'just now' : `${Math.floor(seconds / 60) || 1} min ago`
}
function formatWindow(start: string, end: string) {
  return `${formatTime(start)}–${formatTime(end)}`
}
function displayPollutant(value: string) {
  return value.replace('PM25', 'PM2.5').replace('PM10', 'PM10').replace('NO2', 'NO₂').replace('CO', 'CO')
}
function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b[a-z]/g, (char) => char.toUpperCase())
}
function categoryForAqi(value: number) {
  if (value <= 50) return 'Good'
  if (value <= 100) return 'Satisfactory'
  if (value <= 200) return 'Moderate'
  if (value <= 300) return 'Poor'
  if (value <= 400) return 'Very Poor'
  return 'Severe'
}
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function isBriefContent(value: unknown): value is BriefContent {
  return isRecord(value)
    && typeof value.headline === 'string'
    && typeof value.summary === 'string'
    && Array.isArray(value.hypotheses)
    && Array.isArray(value.verification_steps)
    && Array.isArray(value.recommended_actions)
    && Array.isArray(value.unknowns)
    && typeof value.disclaimer === 'string'
}
function isPassage(value: unknown): value is Passage {
  return isRecord(value)
    && typeof value.doc_key === 'string'
    && typeof value.title === 'string'
    && typeof value.passage_id === 'string'
    && typeof value.excerpt === 'string'
    && (typeof value.version === 'string' || value.version === null || value.version === undefined)
    && (typeof value.url === 'string' || value.url === null || value.url === undefined)
}
function peakCategory(incident: Incident) {
  const ranges: Record<string, Array<[number, number, number, number]>> = {
    PM25: [[0, 30, 0, 50], [31, 60, 51, 100], [61, 90, 101, 200], [91, 120, 201, 300], [121, 250, 301, 400], [251, 500, 401, 500]],
    PM10: [[0, 50, 0, 50], [51, 100, 51, 100], [101, 250, 101, 200], [251, 350, 201, 300], [351, 430, 301, 400], [431, 600, 401, 500]],
    NO2: [[0, 40, 0, 50], [41, 80, 51, 100], [81, 180, 101, 200], [181, 280, 201, 300], [281, 400, 301, 400], [401, 800, 401, 500]],
  }
  const points = ranges[incident.pollutant.toUpperCase()]
  if (!points) return 'No Data'
  const concentration = Math.round(incident.peakValue)
  let index = -1
  for (const [low, high, indexLow, indexHigh] of points) {
    if (concentration >= low && concentration <= high) {
      index = Math.round(((indexHigh - indexLow) / (high - low)) * (concentration - low) + indexLow)
      break
    }
  }
  const last = points[points.length - 1]
  if (index < 0 && last && concentration > last[1]) index = 500
  return index < 0 ? 'No Data' : categoryForAqi(index)
}
function citizenMeaning(category: string) {
  return ({
    Good: 'Air quality is in the good range.',
    Satisfactory: 'Air quality is generally satisfactory.',
    Moderate: 'Air quality is in the moderate range.',
    Poor: 'Air quality is in the poor range.',
    'Very Poor': 'Air quality is in the very poor range.',
    Severe: 'Air quality is in the severe range.',
  }[category] ?? 'Air quality information is available.')
}
function auditDescription(event: AuditEvent) {
  if (event.eventType === 'INCIDENT_DETECTED') return 'An elevated reading window was detected for review.'
  if (event.eventType === 'QC_COMPLETED') return 'Automated quality-control checks were recorded for the incident.'
  if (event.eventType === 'HYPOTHESES_COMPUTED') return 'Rule-based source-type hypotheses were ranked from available evidence.'
  if (event.eventType === 'BRIEF_GENERATED') return 'An AI-assisted officer brief was generated and passed through validation.'
  try {
    const payload = JSON.parse(event.payloadJson) as { reviewer?: string; decision?: string }
    return `Review recorded${payload.reviewer ? ` by ${payload.reviewer}` : ''}${payload.decision ? ` · ${humanize(payload.decision)}` : ''}.`
  } catch { return 'An audit event was recorded.' }
}

export default App

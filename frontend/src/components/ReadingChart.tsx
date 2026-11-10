import { Area, AreaChart, CartesianGrid, ReferenceArea, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Incident, Reading } from '../types/api'

interface ReadingChartProps {
  readings: Reading[]
  incident: Incident
  neighbors: Array<{ station: { name: string }; readings: Reading[] }>
}

export default function ReadingChart({ readings, incident, neighbors }: ReadingChartProps) {
  const data = readings.map((reading, index) => ({
    time: new Date(reading.observedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    value: reading.value,
    flagged: reading.qualityStatus !== 'GOOD',
    ...Object.fromEntries(neighbors.map((neighbor, neighborIndex) => [`nearby-${neighborIndex}`, neighbor.readings[index]?.value])),
  }))
  return <div className="chart-wrap" role="img" aria-label={`${readings.length} synthetic ${incident.pollutant} readings with a baseline band and highlighted anomaly window`}>
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 12, right: 12, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="readingFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#0d7770" stopOpacity={0.19} /><stop offset="100%" stopColor="#0d7770" stopOpacity={0.015} /></linearGradient>
        </defs>
        <CartesianGrid stroke="var(--chart-grid)" vertical={false} strokeDasharray="3 5" />
        <XAxis dataKey="time" tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 14 }} minTickGap={45} />
        <YAxis tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 14 }} width={45} />
        <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, color: 'var(--text)' }} formatter={(value, name) => [`${Number(value).toFixed(1)} µg/m³`, name === 'value' ? 'Observed' : String(name)]} />
        <ReferenceArea y1={incident.baselineValue * 0.82} y2={incident.baselineValue * 1.18} fill="#0d7770" fillOpacity={0.075} />
        <ReferenceArea x1={data[13]?.time} x2={data[21]?.time} fill="#e9a23b" fillOpacity={0.1} />
        <Area dataKey={() => incident.baselineValue} name="Baseline" stroke="#90a6a2" strokeDasharray="4 4" fill="none" dot={false} isAnimationActive={false} />
        <Area dataKey="value" stroke="#0d7770" strokeWidth={2.5} fill="url(#readingFill)" activeDot={{ r: 4, fill: '#0d7770', stroke: 'var(--surface)', strokeWidth: 2 }} isAnimationActive={false} />
        {neighbors.map((neighbor, index) => <Area key={neighbor.station.name} dataKey={`nearby-${index}`} name={neighbor.station.name} stroke={['#9aa9bd', '#ba9b7a', '#8ba3a0'][index]} strokeOpacity={0.78} strokeWidth={1.4} fill="none" dot={false} isAnimationActive={false} />)}
      </AreaChart>
    </ResponsiveContainer>
    {data.some((item) => item.flagged) && <span className="qc-marker" title="Quality-control flag"><span className="sr-only">Quality-control flag marker</span>!</span>}
  </div>
}

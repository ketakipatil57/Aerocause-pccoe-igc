import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { getMockEvaluation, resetMockState, stopMockReplay } from './data/mock'
import citizenPrecautions from './data/citizen-precautions.json'

function renderApp(initialEntry = '/incidents') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}><App /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AeroCause workspace', () => {
  beforeEach(() => {
    resetMockState()
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Backend is unavailable')))
  })

  afterEach(() => {
    cleanup()
    stopMockReplay()
    vi.unstubAllGlobals()
  })

  it('provides CPCB health-impact descriptions for every AQI category', () => {
    expect(Object.keys(citizenPrecautions.categories)).toEqual([
      'Good',
      'Satisfactory',
      'Moderate',
      'Poor',
      'Very Poor',
      'Severe',
    ])
    expect(Object.values(citizenPrecautions.categories).every((text) => !text.includes('placeholder'))).toBe(true)
    expect(citizenPrecautions.needs_native_speaker_review).toBe(true)
    expect(citizenPrecautions.source.publisher).toBe('Central Pollution Control Board (CPCB)')
  })

  it('labels local sample data and opens a ranked incident detail', async () => {
    renderApp()

    expect(await screen.findByRole('heading', { name: 'Incident desk' })).toBeInTheDocument()
    expect(await screen.findByText('SAMPLE DATA (synthetic)')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('link', { name: /Central Monitor/ }))

    expect(await screen.findByRole('heading', { name: /Central Monitor/ })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Ranked source-type hypotheses' })).toBeInTheDocument()
    expect(screen.getAllByText('Evidence FOR').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Evidence AGAINST').length).toBeGreaterThan(0)
  })

  it('supports replay-to-brief-to-review in mock-data mode', async () => {
    renderApp('/replay')
    fireEvent.click(await screen.findByRole('button', { name: 'Start replay' }))
    expect(await screen.findByText('IN PROGRESS')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: /Incidents/ }))
    fireEvent.click(await screen.findByRole('link', { name: /North Station/ }))

    expect(await screen.findByRole('heading', { name: /North Station/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: /Officer brief/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Generate brief/ }))

    expect(await screen.findByRole('heading', { name: /Elevated particulate reading/ })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Review decision'), { target: { value: 'NEEDS_MORE_EVIDENCE' } })
    fireEvent.change(screen.getByLabelText('Reviewer name'), { target: { value: 'Demo officer' } })
    fireEvent.click(screen.getByRole('button', { name: /Save review/ }))

    await waitFor(() => expect(screen.getAllByText('UNDER REVIEW').length).toBeGreaterThan(0))
    fireEvent.click(screen.getByRole('tab', { name: /Timeline \/ Audit/ }))
    expect(await screen.findByText('Review Submitted')).toBeInTheDocument()
  })

  it('disables evaluation and explains when a replay is active', async () => {
    renderApp('/replay')
    fireEvent.click(await screen.findByRole('button', { name: 'Start replay' }))
    fireEvent.click(screen.getByRole('link', { name: /Evaluation/ }))

    expect(await screen.findByText('Active replay in progress. Please stop the replay from the Replay page before running pipeline evaluation.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run evaluation' })).toBeDisabled()
  })

  it('shows a friendly conflict notice if evaluation races with replay start', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/replay/status')) {
        return new Response(JSON.stringify({ running: false, episodeId: null, progress: 0, readingsProcessed: 0, incidentsCreated: 0 }), { status: 200 })
      }
      if (url.endsWith('/evaluation/latest')) {
        return new Response(JSON.stringify(getMockEvaluation()), { status: 200 })
      }
      if (url.endsWith('/evaluation/run') && init?.method === 'POST') {
        return new Response('Replay active', { status: 409 })
      }
      return new Response('Not found', { status: 404 })
    })
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/evaluation')

    fireEvent.click(await screen.findByRole('button', { name: 'Run evaluation' }))

    expect(await screen.findByText('Active replay in progress. Please stop the replay from the Replay page before running pipeline evaluation.')).toBeInTheDocument()
    expect(screen.queryByText(/Request failed \(409\)/)).not.toBeInTheDocument()
  })

  it('renders CPCB AQI health impacts and flags translations for native-speaker review', async () => {
    renderApp('/citizen')

    expect(await screen.findByRole('heading', { name: /Air quality, clearly explained/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'मराठी' }))
    expect(await screen.findByText(/उपलब्ध पुराव्याचा संबंध/)).toBeInTheDocument()
    expect(screen.getByText(/May cause breathing discomfort on prolonged exposure/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Source: Central Pollution Control Board/ })).toHaveAttribute('href', 'https://cpcb.nic.in/National-Air-Quality-Index/')
    expect(screen.queryByText(/PLACEHOLDER/)).not.toBeInTheDocument()
    expect(screen.getByText(/Needs native-speaker review/)).toBeInTheDocument()
  })
})

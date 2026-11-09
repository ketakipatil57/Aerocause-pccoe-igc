import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { resetMockState, stopMockReplay } from './data/mock'

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

  it('renders the citizen language switch and explicit precaution placeholder', async () => {
    renderApp('/citizen')

    expect(await screen.findByRole('heading', { name: /Air quality, clearly explained/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'मराठी' }))
    expect(await screen.findByText(/उपलब्ध पुराव्याचा संबंध/)).toBeInTheDocument()
    expect(screen.getByText(/PLACEHOLDER · NOT HEALTH ADVICE/)).toBeInTheDocument()
    expect(screen.getByText(/Needs native-speaker review/)).toBeInTheDocument()
  })
})

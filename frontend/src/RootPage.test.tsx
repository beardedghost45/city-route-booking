import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import RootPage from './RootPage'

describe('application routes', () => {
  it('renders the admin sign-in at /admin, including a trailing slash', () => {
    render(<RootPage pathname="/admin/" />)

    expect(screen.getByRole('heading', { name: 'Bookings overview' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Admin sign in' })).toBeInTheDocument()
  })

  it('keeps the existing customer booking page at the root route', () => {
    render(<RootPage pathname="/" />)

    expect(screen.getByRole('heading', { name: 'Where are you headed?' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Bookings overview' })).not.toBeInTheDocument()
  })
})

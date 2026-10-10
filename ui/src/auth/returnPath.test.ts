import { describe, expect, it } from 'vitest'
import { returnPathFromLocation, safeReturnPath } from './returnPath'

describe('safeReturnPath', () => {
  it('keeps valid protected workspace paths and their query and hash', () => {
    expect(safeReturnPath('/dashboard')).toBe('/dashboard')
    expect(safeReturnPath('/transactions?page=2#recent')).toBe('/transactions?page=2#recent')
    expect(safeReturnPath('/documents')).toBe('/documents')
    expect(safeReturnPath('/documents/upload')).toBe('/documents/upload')
    expect(safeReturnPath('/documents/4?page=2#row')).toBe('/documents/4?page=2#row')
  })

  it('falls back to Dashboard for external, public, or unsafe paths', () => {
    expect(safeReturnPath(null)).toBe('/dashboard')
    expect(safeReturnPath('/login')).toBe('/dashboard')
    expect(safeReturnPath('https://evil.example/documents')).toBe('/dashboard')
    expect(safeReturnPath('//evil.example/documents')).toBe('/dashboard')
    expect(safeReturnPath('/documents/../login')).toBe('/dashboard')
    expect(safeReturnPath('/documents/%2e%2e/login')).toBe('/dashboard')
    expect(safeReturnPath('/documents/4/anything')).toBe('/dashboard')
  })
})

describe('returnPathFromLocation', () => {
  it('prefers the protected-route location, including search and hash', () => {
    expect(returnPathFromLocation(
      { from: { pathname: '/documents/9', search: '?page=2', hash: '#row' } },
      '?next=/documents/4',
    )).toBe('/documents/9?page=2#row')
  })

  it('uses the next query when no location state is present', () => {
    expect(returnPathFromLocation(null, '?next=%2Fdocuments%2F4%3Fpage%3D2')).toBe('/documents/4?page=2')
  })

  it('falls back to Dashboard for an unsafe next query', () => {
    expect(returnPathFromLocation(null, '?next=https%3A%2F%2Fevil.example')).toBe('/dashboard')
  })
})

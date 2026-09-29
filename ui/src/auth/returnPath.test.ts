import { describe, expect, it } from 'vitest'
import { returnPathFromLocation, safeReturnPath } from './returnPath'

describe('safeReturnPath', () => {
  it('keeps document paths and drops everything else', () => {
    expect(safeReturnPath('/documents/4?page=2#row')).toBe('/documents/4?page=2#row')
    expect(safeReturnPath('/login')).toBe('/documents')
    expect(safeReturnPath('//evil.example/documents')).toBe('/documents')
    expect(safeReturnPath('/documents/../login')).toBe('/documents')
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
})

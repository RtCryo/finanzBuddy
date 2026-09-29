type FromLocation = {
  pathname?: string
  search?: string
  hash?: string
}

export function safeReturnPath(path: string | null | undefined) {
  if (!path || !isDocumentPath(path)) return '/documents'
  return path
}

export function returnPathFromLocation(state: unknown, search: string) {
  const from = (state as { from?: FromLocation } | null)?.from
  const destination = from?.pathname
    ? `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
    : null
  const requestedPath = new URLSearchParams(search).get('next')
  return safeReturnPath(destination ?? requestedPath)
}

function isDocumentPath(path: string) {
  if (path.includes('\\') || path.includes('://') || path.startsWith('//')) return false
  const pathname = path.split(/[?#]/)[0]
  if (pathname.split('/').includes('..')) return false
  return pathname === '/documents' || pathname.startsWith('/documents/')
}

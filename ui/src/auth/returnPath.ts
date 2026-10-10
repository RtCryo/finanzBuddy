type FromLocation = {
  pathname?: string
  search?: string
  hash?: string
}

export function safeReturnPath(path: string | null | undefined) {
  if (!path || !isProtectedWorkspacePath(path)) return '/dashboard'
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

function isProtectedWorkspacePath(path: string) {
  if (
    !path.startsWith('/')
    || path.startsWith('//')
    || path.includes('\\')
    || path.includes('://')
    || /[\u0000-\u001f\u007f]/.test(path)
    || /%(?:2f|5c|2e)/i.test(path)
  ) return false

  const pathname = path.split(/[?#]/, 1)[0]
  if (pathname.split('/').includes('..')) return false
  return pathname === '/dashboard'
    || pathname === '/transactions'
    || pathname === '/documents'
    || pathname === '/documents/upload'
    || /^\/documents\/\d+$/.test(pathname)
}

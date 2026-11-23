/** Detect phone/tablet browsers used for the dedicated upload entry. */
export function isMobileClient() {
  if (typeof navigator === 'undefined') return false
  return isMobileUserAgent(navigator.userAgent || '')
}

export function isMobileUserAgent(value) {
  return /Android|iPhone|iPad|iPod|Mobile|HarmonyOS|MicroMessenger/i.test(value || '')
}

/** Only allow local application paths as login redirect targets. */
export function safeLocalPath(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : null
}

export function landingPath(redirect, mobile) {
  return safeLocalPath(redirect) || (mobile ? '/mobile' : '/workbench')
}

export function canAccessRoles(role, allowedRoles) {
  return !allowedRoles?.length || allowedRoles.includes(role)
}

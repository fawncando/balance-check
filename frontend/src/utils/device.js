/** Detect phone/tablet browsers used for the dedicated upload entry. */
export function isMobileClient() {
  if (typeof navigator === 'undefined' || typeof window === 'undefined') return false
  return /Android|iPhone|iPad|iPod|Mobile|HarmonyOS|MicroMessenger/i
    .test(navigator.userAgent || '') || window.matchMedia('(max-width: 640px)').matches
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

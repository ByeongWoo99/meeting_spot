export function formatSeconds(seconds) {
  if (seconds < 0) return '-'
  const m = Math.floor(seconds / 60)
  if (m >= 60) return `${Math.floor(m / 60)}시간 ${m % 60}분`
  return `${m}분`
}

export function formatDistance(meters) {
  if (meters < 0) return '-'
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)}km`
  return `${meters}m`
}

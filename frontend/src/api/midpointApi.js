import axios from 'axios'

export async function calcMidpoint(locations, category = 'ALL', signal, sessionKey) {
  const { data } = await axios.post('/api/midpoint', { locations, category, sessionKey }, { signal })
  return { candidates: data.candidates, searchNote: data.searchNote }
}

export async function sendEvent(sessionKey, eventType, eventValue) {
  if (!sessionKey) return
  await axios.post('/api/events', {
    sessionKey,
    eventType,
    eventValue: JSON.stringify(eventValue),
  }).catch(() => {})
}

export async function describeCandidate(candidate) {
  const { data } = await axios.post('/api/midpoint/describe', {
    stationName: candidate.nearestStation,
    address: candidate.address,
    transitTimes: candidate.transitTimes,
  })
  return data.description
}

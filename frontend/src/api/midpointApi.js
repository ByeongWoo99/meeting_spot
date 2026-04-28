import axios from 'axios'

export async function calcMidpoint(locations, signal) {
  const { data } = await axios.post('/api/midpoint', { locations }, { signal })
  return data.candidates
}

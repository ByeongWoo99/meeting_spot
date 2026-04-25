import axios from 'axios'

export async function calcMidpoint(locations) {
  const { data } = await axios.post('/api/midpoint', { locations })
  return data.candidates
}

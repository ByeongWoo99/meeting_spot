import axios from 'axios'

export async function calcMidpoint(locations, category = 'ALL', signal) {
  const { data } = await axios.post('/api/midpoint', { locations, category }, { signal })
  return { candidates: data.candidates, searchNote: data.searchNote }
}

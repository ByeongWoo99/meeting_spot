import axios from 'axios'

export async function fetchPlaces(lat, lng, category = 'ALL', radius = 1000) {
  const { data } = await axios.get('/api/places', { params: { lat, lng, category, radius } })
  return data
}

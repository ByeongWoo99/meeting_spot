import axios from 'axios'

export async function fetchCarDirections(locations, destination) {
  const { data } = await axios.post('/api/directions/car', { locations, destination })
  return data
}

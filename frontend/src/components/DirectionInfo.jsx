import { useState } from 'react'
import { formatSeconds, formatDistance } from '../utils/format'

const MARKER_COLORS = ['#3B82F6', '#EF4444', '#22C55E', '#A855F7', '#F97316', '#EC4899']

// mode: 'car' | 'traffic'
// 목적지는 실제 역 좌표(stationLat, stationLng) 사용
function buildKakaoUrl(mode, startName, startLat, startLng, endName, endLat, endLng) {
  const start = `${encodeURIComponent(startName)},${startLat},${startLng}`
  const end = `${encodeURIComponent(endName)},${endLat},${endLng}`
  return `https://map.kakao.com/link/by/${mode}/${start}/${end}`
}

function RouteList({ directions, loading, users, midpoint, mode }) {
  if (loading) return <div className="text-center py-4 text-gray-400 text-xs">경로 계산 중...</div>

  const validUsers = (users || []).filter(u => u.lat && u.lng)
  if (validUsers.length === 0) return null

  // 자가용: 소요시간 비율 계산
  const maxDuration = directions
    ? Math.max(...directions.map(d => d.duration).filter(d => d > 0))
    : 0

  // 목적지 좌표: stationLat/stationLng 우선, 없으면 midpoint lat/lng
  const destLat = midpoint?.stationLat || midpoint?.lat
  const destLng = midpoint?.stationLng || midpoint?.lng
  const destName = midpoint?.address || midpoint?.nearestStation || '목적지'

  return (
    <div className="flex flex-col gap-2">
      {validUsers.map((user, i) => {
        const color = MARKER_COLORS[i % MARKER_COLORS.length]
        const dir = directions?.[i]
        const ratio = maxDuration > 0 && dir?.duration > 0 ? dir.duration / maxDuration : 0
        const url = buildKakaoUrl(mode, user.address, user.lat, user.lng, destName, destLat, destLng)

        return (
          <div key={i} className="bg-gray-50 rounded-xl px-3 py-2">
            <div className="flex items-center justify-between mb-1">
              <div className="flex items-center gap-2">
                <span className="w-5 h-5 rounded-full text-white text-xs flex items-center justify-center font-bold flex-shrink-0"
                  style={{ backgroundColor: color }}>
                  {i + 1}
                </span>
                <span className="text-xs text-gray-600 font-medium truncate max-w-[110px]">{user.address}</span>
              </div>
              <div className="flex items-center gap-2">
                {dir && dir.duration > 0 && (
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <span className="font-semibold text-gray-800">{formatSeconds(dir.duration)}</span>
                    {mode === 'car' && <span>{formatDistance(dir.distance)}</span>}
                    {mode === 'car' && dir.tollFee > 0 && (
                      <span className="text-orange-500">톨비 {dir.tollFee.toLocaleString()}원</span>
                    )}
                    {mode === 'car' && (
                      <span className="text-gray-400">(평균 기준)</span>
                    )}
                  </div>
                )}
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs bg-yellow-400 hover:bg-yellow-500 text-gray-800 font-semibold px-2 py-1 rounded-lg transition-colors flex-shrink-0"
                >
                  지도보기
                </a>
              </div>
            </div>
            {mode === 'car' && dir?.duration > 0 && (
              <div className="h-1.5 bg-gray-200 rounded-full overflow-hidden">
                <div className="h-full rounded-full transition-all duration-500"
                  style={{ width: `${ratio * 100}%`, backgroundColor: color }} />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

export default function DirectionInfo({ carDirections, carLoading, transitDirections, users, midpoint }) {
  const [tab, setTab] = useState('car')

  return (
    <div>
      <div className="flex gap-2 mb-3">
        <button
          onClick={() => setTab('car')}
          className={`text-xs px-3 py-1.5 rounded-full font-medium transition-colors
            ${tab === 'car' ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
        >
          🚗 자가용
        </button>
        <button
          onClick={() => setTab('transit')}
          className={`text-xs px-3 py-1.5 rounded-full font-medium transition-colors
            ${tab === 'transit' ? 'bg-yellow-400 text-gray-800' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
        >
          🚇 대중교통
        </button>
      </div>

      <RouteList
        directions={tab === 'car' ? carDirections : transitDirections}
        loading={tab === 'car' ? carLoading : false}
        users={users}
        midpoint={midpoint}
        mode={tab === 'car' ? 'car' : 'traffic'}
      />
    </div>
  )
}

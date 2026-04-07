const CATEGORY_COLORS = {
  FD6: 'bg-orange-100 text-orange-700',
  CE7: 'bg-yellow-100 text-yellow-700',
  AT4: 'bg-green-100 text-green-700',
  CT1: 'bg-purple-100 text-purple-700',
}

const CATEGORY_LABELS = {
  FD6: '맛집',
  CE7: '카페',
  AT4: '명소',
  CT1: '문화시설',
}

export default function PlaceCard({ place, onClick, selected }) {
  const colorClass = CATEGORY_COLORS[place.categoryCode] || 'bg-gray-100 text-gray-600'
  const categoryLabel = CATEGORY_LABELS[place.categoryCode] || place.categoryCode

  return (
    <div
      onClick={() => onClick(place)}
      className={`p-4 rounded-xl border cursor-pointer transition-all
        ${selected ? 'border-blue-400 bg-blue-50 shadow-md' : 'border-gray-100 bg-white hover:border-gray-300 hover:shadow-sm'}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${colorClass}`}>
              {categoryLabel}
            </span>
            <span className="text-xs text-gray-400">{place.distance}m</span>
          </div>
          <h3 className="font-semibold text-gray-800 text-sm truncate">{place.name}</h3>
          <p className="text-xs text-gray-400 mt-0.5 truncate">{place.address}</p>
          {place.phone && (
            <p className="text-xs text-gray-400 mt-0.5">{place.phone}</p>
          )}
        </div>
        {place.placeUrl && (
          <a
            href={place.placeUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => e.stopPropagation()}
            className="text-xs text-blue-500 hover:underline flex-shrink-0"
          >
            상세보기
          </a>
        )}
      </div>
    </div>
  )
}

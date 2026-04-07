import { useState, useRef, useEffect } from 'react'

export default function LocationInput({ index, value, onChange }) {
  const [keyword, setKeyword] = useState(value.address || '')
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const debounceRef = useRef(null)
  const wrapperRef = useRef(null)

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    function handleClick(e) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function handleInput(e) {
    const val = e.target.value
    setKeyword(val)
    onChange({ address: val, lat: null, lng: null })

    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!val.trim()) { setSuggestions([]); setOpen(false); return }

    debounceRef.current = setTimeout(() => {
      if (!window.kakao?.maps) return
      window.kakao.maps.load(() => {
        const ps = new window.kakao.maps.services.Places()
        ps.keywordSearch(val, (data, status) => {
          if (status === window.kakao.maps.services.Status.OK) {
            setSuggestions(data.slice(0, 6))
            setOpen(true)
          } else {
            setSuggestions([])
            setOpen(false)
          }
        })
      })
    }, 300)
  }

  function handleSelect(place) {
    setKeyword(place.place_name)
    setSuggestions([])
    setOpen(false)
    onChange({
      address: place.place_name,
      lat: parseFloat(place.y),
      lng: parseFloat(place.x),
    })
  }

  const colors = ['blue', 'red', 'green', 'purple', 'orange', 'pink']
  const color = colors[index % colors.length]

  return (
    <div ref={wrapperRef} className="relative mb-3">
      <div className="flex items-center gap-2">
        <span className={`w-6 h-6 rounded-full bg-${color}-500 text-white text-xs flex items-center justify-center flex-shrink-0 font-bold`}>
          {index + 1}
        </span>
        <input
          type="text"
          value={keyword}
          onChange={handleInput}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          placeholder={`출발지 ${index + 1} 검색`}
          className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
      </div>

      {open && suggestions.length > 0 && (
        <ul className="absolute left-8 right-0 top-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 overflow-hidden">
          {suggestions.map((place) => (
            <li
              key={place.id}
              onMouseDown={() => handleSelect(place)}
              className="px-4 py-2 hover:bg-blue-50 cursor-pointer"
            >
              <div className="text-sm font-medium text-gray-800">{place.place_name}</div>
              <div className="text-xs text-gray-400">{place.road_address_name || place.address_name}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

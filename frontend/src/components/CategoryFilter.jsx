const CATEGORIES = [
  { code: 'ALL', label: '전체' },
  { code: 'FD6', label: '맛집' },
  { code: 'CE7', label: '카페' },
  { code: 'AT4', label: '명소' },
  { code: 'CT1', label: '문화시설' },
]

export default function CategoryFilter({ selected, onChange }) {
  return (
    <div className="flex gap-2 mb-4 flex-wrap">
      {CATEGORIES.map(({ code, label }) => (
        <button
          key={code}
          onClick={() => onChange(code)}
          className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors
            ${selected === code
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
        >
          {label}
        </button>
      ))}
    </div>
  )
}

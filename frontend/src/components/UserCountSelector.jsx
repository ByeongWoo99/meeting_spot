export default function UserCountSelector({ count, onChange }) {
  return (
    <div className="flex items-center gap-3 mb-6">
      <span className="text-sm font-medium text-gray-600">인원 수</span>
      <div className="flex gap-2">
        {[2, 3, 4, 5, 6].map((n) => (
          <button
            key={n}
            onClick={() => onChange(n)}
            className={`w-9 h-9 rounded-full text-sm font-semibold transition-colors
              ${count === n
                ? 'bg-blue-500 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
          >
            {n}
          </button>
        ))}
      </div>
    </div>
  )
}

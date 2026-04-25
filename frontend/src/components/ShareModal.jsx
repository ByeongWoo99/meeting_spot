import { useState } from 'react'
import { shareViaKakao } from '../utils/kakaoShare'

export default function ShareModal({ url, title, description, onClose }) {
  const [copied, setCopied] = useState(false)

  function handleKakao() {
    shareViaKakao({ url, title, description })
    onClose()
  }

  function handleCopy() {
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true)
      setTimeout(() => { setCopied(false); onClose() }, 1500)
    })
  }

  return (
    <div
      className="fixed inset-0 bg-black/40 flex items-end justify-center z-50"
      onClick={onClose}
    >
      <div
        className="bg-white w-full max-w-2xl rounded-t-2xl p-6 pb-8"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-10 h-1 bg-gray-300 rounded-full mx-auto mb-5" />
        <p className="text-sm font-bold text-gray-700 mb-5 text-center">공유하기</p>
        <div className="flex justify-center gap-10 mb-6">
          <button onClick={handleKakao} className="flex flex-col items-center gap-2">
            <div className="w-14 h-14 bg-yellow-400 rounded-full flex items-center justify-center">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="#3C1E1E">
                <path d="M12 3C6.48 3 2 6.69 2 11.25c0 2.91 1.75 5.47 4.39 6.97l-.9 3.28 3.82-2.53c.67.14 1.37.21 2.09.21 5.52 0 10-3.69 10-8.1S17.52 3 12 3z" />
              </svg>
            </div>
            <span className="text-xs text-gray-600 font-medium">카카오톡</span>
          </button>
          <button onClick={handleCopy} className="flex flex-col items-center gap-2">
            <div className="w-14 h-14 bg-gray-100 rounded-full flex items-center justify-center">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#6B7280" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
              </svg>
            </div>
            <span className="text-xs text-gray-600 font-medium">{copied ? '복사됨!' : 'URL 복사'}</span>
          </button>
        </div>
        <button
          onClick={onClose}
          className="w-full text-sm text-gray-400 py-2 hover:text-gray-600 transition-colors"
        >
          닫기
        </button>
      </div>
    </div>
  )
}

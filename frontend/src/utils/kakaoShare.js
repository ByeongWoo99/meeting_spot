export function shareViaKakao({ url, title, description }) {
  if (!window.Kakao) return false
  try {
    if (!window.Kakao.isInitialized()) {
      window.Kakao.init(import.meta.env.VITE_KAKAO_JS_KEY)
    }
    window.Kakao.Share.sendDefault({
      objectType: 'feed',
      content: {
        title,
        description,
        imageUrl: 'https://mud-kage.kakao.com/dn/dpwvVx/btqB3iSHrOe/xKSTnFqHQXNpnmKbSTqfTk/kakaolink40_original.png',
        link: { mobileWebUrl: url, webUrl: url },
      },
      buttons: [{ title: '결과 보기', link: { mobileWebUrl: url, webUrl: url } }],
    })
    return true
  } catch {
    return false
  }
}

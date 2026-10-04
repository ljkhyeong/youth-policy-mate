type PreviewLoadResult<T> = { status: "available"; examples: readonly T[] } | { status: "unavailable" };

// 개발 전용 서버 컴포넌트에서만 호출한다. 사용자 입력·회원 정보로 조회 주소나 요청을 바꾸지 않는다.
export async function loadPreviewExamples<B, T>(path: string, toView: (body: B) => readonly T[]): Promise<PreviewLoadResult<T>> {
  if (process.env.NODE_ENV !== "development") return { status: "unavailable" };
  try {
    const response = await fetch(`http://127.0.0.1:8081/api/dev/${path}`, {
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
      headers: { Accept: "application/json" },
    });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return { status: "unavailable" };
    return { status: "available", examples: toView(await response.json() as B) };
  } catch {
    // 외부 오류 본문·내부 주소를 표시하지 않고, 고정 예시로 조용히 대체하지 않는다.
    return { status: "unavailable" };
  }
}

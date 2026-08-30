export default function HomePage() {
  return (
    <div className="mx-auto flex min-h-svh max-w-6xl flex-col px-6 sm:px-10">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-stone-300 py-7">
        <span className="text-lg font-bold tracking-tight">청년정책메이트</span>
        <span className="text-sm text-stone-600">로컬 개발 환경</span>
      </header>

      <main className="grid flex-1 content-center gap-12 py-16 lg:grid-cols-[1.4fr_1fr] lg:gap-20 lg:py-28">
        <section aria-labelledby="intro-title">
          <p className="mb-6 text-sm font-semibold text-teal-800">첫 시작은 서울에서</p>
          <h1 id="intro-title" className="text-4xl leading-[1.3] font-bold tracking-tight sm:text-5xl">
            내게 필요한 정책,
            <br />
            근거부터 꼼꼼하게.
          </h1>
          <p className="mt-7 max-w-md text-base leading-8 text-stone-600">
            서울 청년을 위한 정책 안내를 준비하고 있어요.
            내 조건에 맞는 정책을 확인하고, 관심 정책의 신청 마감을 챙길 수 있는 서비스를 만들고 있습니다.
          </p>
          <a
            className="mt-9 inline-flex min-h-11 items-center border-b border-teal-800 pb-1 text-sm font-semibold text-teal-900 transition-colors hover:text-teal-700 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-teal-800"
            href="https://www.youthcenter.go.kr/"
          >
            지금은 온통청년에서 정책 확인하기 ↗
          </a>
        </section>

        <aside aria-labelledby="status-title" className="self-center border-l-2 border-teal-800 bg-white p-7 sm:p-9">
          <p className="text-xs font-semibold tracking-widest text-stone-500">개발 진행 안내</p>
          <h2 id="status-title" className="mt-5 text-xl font-bold tracking-tight">정책 데이터 연결 준비 중</h2>
          <p className="mt-4 text-sm leading-7 text-stone-600">
            온통청년 API 인증키를 신청하고 승인을 기다리고 있습니다. 승인 후 실제 응답을 확인해 정책 수집을 연결할 예정입니다.
          </p>
          <div className="mt-7 border-t border-stone-200 pt-5 text-sm leading-7 text-stone-600">
            현재 화면은 기본 개발 환경 확인용입니다.
            정책 조회·로그인·관심 정책 저장은 아직 제공하지 않습니다.
          </div>
        </aside>
      </main>

      <footer className="border-t border-stone-300 py-6 text-xs leading-6 text-stone-500">
        서비스 준비 중 · 실제 정책 신청과 최종 자격 확인은 공식 신청처에서 진행합니다.
      </footer>
    </div>
  );
}

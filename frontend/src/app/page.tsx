import Link from "next/link";
import { SiteShell } from "@/components/site-shell";

export default function HomePage() {
  return (
    <SiteShell active="home">
      <main id="main-content" className="grid flex-1 content-center gap-12 py-16 lg:grid-cols-[1.4fr_1fr] lg:gap-20 lg:py-28">
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
          <div className="mt-9 flex flex-wrap items-center gap-x-7 gap-y-4">
            <Link href="/conditions" className="button-primary">내 조건 입력하기 <span aria-hidden="true">→</span></Link>
            <a className="text-link" href="https://www.youthcenter.go.kr/">온통청년에서 정책 보기 ↗</a>
          </div>
        </section>

        <aside aria-labelledby="status-title" className="self-center border-l-2 border-teal-800 bg-white p-7 sm:p-9">
          <p className="text-xs font-semibold tracking-widest text-stone-500">개발 진행 안내</p>
          <h2 id="status-title" className="mt-5 text-xl font-bold tracking-tight">정책 데이터 연결 준비 중</h2>
          <p className="mt-4 text-sm leading-7 text-stone-600">
            온통청년 API 인증키를 신청하고 승인을 기다리고 있습니다. 승인 후 실제 응답을 확인해 정책 수집을 연결할 예정입니다.
          </p>
          <div className="mt-7 border-t border-stone-200 pt-5 text-sm leading-7 text-stone-600">
            로그인 없이 내 조건을 입력하고 확인할 수 있습니다.
            정책 추천·로그인·관심 정책 저장은 아직 제공하지 않습니다.
          </div>
        </aside>
      </main>

    </SiteShell>
  );
}

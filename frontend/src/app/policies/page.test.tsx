import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { components } from "@/generated/policy-api";
import PoliciesPage, { generateMetadata } from "./page";
import { loadPolicies } from "./load-policies";

vi.mock("./load-policies", () => ({ loadPolicies: vi.fn() }));
afterEach(() => { vi.resetAllMocks(); vi.unstubAllEnvs(); });

const policy: components["schemas"]["PolicySummary"] = {
  policyNumber: "123", title: "시험 지원", description: "지원 안내", category: "교육",
  organization: "시험 기관", applicationPeriod: "공식 안내 확인", collectedAt: "2026-09-05T01:00:00Z",
  questionnaireAvailable: true,
  recruitment: { status: "UNKNOWN", explanation: "신청기간을 확인해주세요.", evaluatedAt: "2026-09-06T00:00:00Z", deadlineOnSeoul: null, daysUntilDeadline: null },
};

// 화면이 서버에 보내는 조회 조건이다. 고르지 않은 필터는 빈 값으로 함께 전달된다.
const listQuery = (overrides: NonNullable<Parameters<typeof loadPolicies>[0]> = {}) =>
  ({ query: "", page: 1, questionsOnly: false, recruitmentStatus: "", categories: [], district: "", pageSize: 20, ...overrides });

describe("정책 목록의 공통요건 질문 탐색", () => {
  it("운영 목록은 페이지별 대표 주소를 쓰고 검색어·필터는 검색 노출에서 제외한다", async () => {
    vi.stubEnv("NODE_ENV", "production"); vi.stubEnv("PUBLIC_APP_URL", "https://policy.example.test");
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 2, pageSize: 20, total: 41, hasNext: true } });
    const metadata = await generateMetadata({ searchParams: Promise.resolve({ page: "2", q: "" }) });
    expect(metadata.alternates).toEqual({ canonical: "https://policy.example.test/policies?page=2" });
    expect(metadata.robots).toEqual({ index: true, follow: true });
    for (const params of [{ q: "개인 검색어" }, { questionsOnly: "true" }, { recruitmentStatus: "OPEN" }]) {
      const filtered = await generateMetadata({ searchParams: Promise.resolve(params) });
      expect(filtered.robots).toEqual({ index: false, follow: true });
      expect(filtered.alternates).toBeUndefined();
      expect(JSON.stringify(filtered)).not.toContain("개인 검색어");
    }
  });

  it("빈 페이지와 조회 실패는 정상 공개 목록으로 검색에 노출하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "production"); vi.stubEnv("PUBLIC_APP_URL", "https://policy.example.test");
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 2, pageSize: 20, total: 1, hasNext: false } });
    expect((await generateMetadata({ searchParams: Promise.resolve({ page: "2" }) })).robots).toEqual({ index: false, follow: false });
    vi.mocked(loadPolicies).mockResolvedValue({ status: "unavailable" });
    expect((await generateMetadata({ searchParams: Promise.resolve({}) })).robots).toEqual({ index: false, follow: false });
  });

  it("검색·페이지 이동에 필터를 유지하고 필터 전환은 첫 페이지로 돌아간다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 2, pageSize: 20, total: 41, hasNext: true } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "시험&지원", page: "2", questionsOnly: "true", recruitmentStatus: "OPEN" }) }));
    expect(loadPolicies).toHaveBeenCalledWith(listQuery({ query: "시험&지원", page: 2, questionsOnly: true, recruitmentStatus: "OPEN" }));
    expect(html).toContain('type="hidden" name="questionsOnly" value="true"');
    const query = encodeURIComponent("시험&지원");
    expect(html).toContain(`href="/policies?q=${query}&amp;page=3&amp;questionsOnly=true&amp;recruitmentStatus=OPEN"`);
    // 켜진 질문 필터 칸을 누르면 필터를 끄고 첫 페이지로 돌아간다.
    expect(html).toContain(`data-selected="true" href="/policies?q=${query}&amp;page=1&amp;recruitmentStatus=OPEN">질문 있는 정책<span class="sr-only"> 선택됨, 누르면 해제</span>`);
    expect(html).toContain(`href="/policies?q=${query}&amp;page=1&amp;questionsOnly=true&amp;recruitmentStatus=OPEN"`);
    expect(html).toContain('href="/policies/123#policy-questions"');
    expect(html).toContain("41건");
    expect(html).toContain('<span aria-hidden="true">2 / 3페이지</span><span class="sr-only">3페이지 중 2페이지</span>');
    expect(html).toContain('value="OPEN" selected=""');
    expect(html).toContain('form="policy-search-form"');
  });

  it("질문이 없는 정책에는 질문 이동을 표시하지 않고 전체 조회를 유지한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [{ ...policy, questionnaireAvailable: false }], page: 1, pageSize: 20, total: 1, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({}) }));
    expect(loadPolicies).toHaveBeenCalledWith(listQuery());
    expect(html).toContain('href="/policies/123"');
    expect(html).not.toContain('href="/policies/123#policy-questions"');
    expect(html).not.toContain('type="hidden" name="questionsOnly"');
    expect(html).toContain('href="/policies?q=&amp;page=1&amp;questionsOnly=true">질문 있는 정책</a>');
  });

  it("질문 필터의 빈 검색 결과를 자격 불충족으로 안내하지 않는다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 1, pageSize: 20, total: 0, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ questionsOnly: "true" }) }));
    expect(html).toContain("질문이 있는 정책 중 검색 결과가 없어요");
    expect(html).toContain("검색·필터 초기화");
    expect(html).toContain("최종 자격과 접수 여부는 공식 신청처에서 확인해주세요");
  });

  it("목록이 줄어든 뒤의 빈 페이지에서는 검색과 필터를 유지해 처음으로 돌아간다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 2, pageSize: 20, total: 1, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "시험", page: "2", questionsOnly: "true" }) }));
    expect(html).toContain("첫 페이지 보기");
    expect(html).toContain(`href="/policies?q=${encodeURIComponent("시험")}&amp;page=1&amp;questionsOnly=true"`);
    expect(html).not.toContain("질문이 있는 정책 중 검색 결과가 없어요");
    // 전체 쪽수(1)를 넘은 쪽에서는 틀린 전체 쪽수 대신 현재 쪽만 보인다.
    expect(html).toContain("<span>2페이지</span>");
    expect(html).not.toContain("페이지 중");
  });

  it("분야 필터는 여러 개를 더하고 빼며 검색어·접수 상태·질문 필터를 유지하고 잘못된 분야는 무시한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 1, pageSize: 20, total: 21, hasNext: true } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "지원", questionsOnly: "true", recruitmentStatus: "OPEN", category: ["HOUSING", "JOB", "UNKNOWN_CATEGORY"] }) }));
    const base = "/policies?q=%EC%A7%80%EC%9B%90&amp;page=1&amp;questionsOnly=true&amp;recruitmentStatus=OPEN";
    // 화면 순서(일자리, 주거…)로 정리해 다음 페이지에도 유지한다.
    expect(loadPolicies).toHaveBeenCalledWith(listQuery({ query: "지원", questionsOnly: true, recruitmentStatus: "OPEN", categories: ["JOB", "HOUSING"] }));
    expect(html).toContain('href="/policies?q=%EC%A7%80%EC%9B%90&amp;page=2&amp;questionsOnly=true&amp;recruitmentStatus=OPEN&amp;category=JOB&amp;category=HOUSING"');
    expect(html).toContain('type="hidden" name="category" value="JOB"');
    expect(html).toContain('type="hidden" name="category" value="HOUSING"');
    // 고른 분야를 누르면 빼고, 고르지 않은 분야를 누르면 더한다.
    expect(html).toContain(`data-selected="true" href="${base}&amp;category=JOB">주거<span class="sr-only"> 선택됨, 누르면 해제</span>`);
    expect(html).toContain(`href="${base}&amp;category=JOB&amp;category=HOUSING&amp;category=FINANCE">금융·복지·문화</a>`);
    expect(html).toContain(`href="${base}">전체 분야</a>`);
    // 질문 필터를 끄면 고른 분야는 그대로 두고 질문 필터만 뺀다.
    expect(html).toContain('data-selected="true" href="/policies?q=%EC%A7%80%EC%9B%90&amp;page=1&amp;recruitmentStatus=OPEN&amp;category=JOB&amp;category=HOUSING">질문 있는 정책');
    expect(html).toContain("‘지원’ 검색 결과 · 일자리, 주거 · 접수 기간");
    expect(html).toContain(">마감 임박순<");
    vi.mocked(loadPolicies).mockClear();
    const unfiltered = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ category: "UNKNOWN_CATEGORY" }) }));
    expect(loadPolicies).toHaveBeenCalledWith(listQuery());
    expect(unfiltered).toContain('data-selected="true" href="/policies?q=&amp;page=1">전체 분야<span class="sr-only"> 선택됨</span>');
    expect(unfiltered).toContain("접수 중인 정책부터 마감 임박순");
    const categoryOnly = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ category: ["EDUCATION", "JOB"] }) }));
    expect(categoryOnly).toContain('<p role="status">일자리, 교육·직업훈련 <strong>');
    const closed = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ recruitmentStatus: "CLOSED" }) }));
    expect(closed).not.toContain("policy-order-note");
  });

  it("지역 필터는 서울 자치구 하나를 골라 다른 필터와 함께 유지하고 공고 지역 기준임을 안내한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 1, pageSize: 20, total: 3, hasNext: true } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "지원", district: "강남구", category: "JOB", recruitmentStatus: "OPEN" }) }));
    expect(loadPolicies).toHaveBeenCalledWith(listQuery({ query: "지원", recruitmentStatus: "OPEN", categories: ["JOB"], district: "강남구" }));
    expect(html).toContain('<option value="">전체 지역</option>');
    expect(html.match(/<option value="[^"]+구"/g)).toHaveLength(25);
    expect(html).toContain('value="강남구" selected=""');
    expect(html).toContain(`href="/policies?q=%EC%A7%80%EC%9B%90&amp;page=2&amp;recruitmentStatus=OPEN&amp;district=${encodeURIComponent("강남구")}&amp;category=JOB"`);
    // 질문 필터를 켜도 지역·분야는 유지한다.
    expect(html).toContain(`href="/policies?q=%EC%A7%80%EC%9B%90&amp;page=1&amp;questionsOnly=true&amp;recruitmentStatus=OPEN&amp;district=${encodeURIComponent("강남구")}&amp;category=JOB">질문 있는 정책</a>`);
    expect(html).toContain("‘지원’ 검색 결과 · 일자리 · 강남구 · 접수 기간");
    expect(html).toContain("선택한 자치구가 공고 지역에 포함된 정책을 보여줘요");
    expect(html).toContain("검색·필터 초기화");
    // 지역 코드 이름·목록 밖 값은 무시하고 전체 지역으로 조회한다.
    vi.mocked(loadPolicies).mockClear();
    const ignored = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ district: "GANGNAM" }) }));
    expect(loadPolicies).toHaveBeenCalledWith(listQuery());
    expect(ignored).not.toContain("공고 지역에 포함된");
    expect(ignored).toContain('value="" selected=""');
    // 지역만 고른 빈 결과는 지역 때문임을 안내하고, 지역 필터 결과는 검색에 노출하지 않는다.
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 1, pageSize: 20, total: 0, hasNext: false } });
    const empty = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ district: "은평구" }) }));
    expect(empty).toContain('<p role="status">은평구 <strong>0건</strong>');
    expect(empty).toContain("선택한 지역이 포함된 정책이 없어요");
    vi.stubEnv("NODE_ENV", "production"); vi.stubEnv("PUBLIC_APP_URL", "https://policy.example.test");
    const metadata = await generateMetadata({ searchParams: Promise.resolve({ district: "은평구" }) });
    expect(metadata.robots).toEqual({ index: false, follow: true });
    expect(metadata.alternates).toBeUndefined();
  });
});

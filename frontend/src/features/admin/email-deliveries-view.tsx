import { PageState } from "@/components/page-state";
import { CollectionFailure, collectionTime } from "./collection-exception-view";
import type { EmailDeliveryPage, LoadFailure } from "./load-collection-exceptions";
import { EmailProviderStatus } from "./email-provider-status";

export const EMAIL_DELIVERIES_PATH = "/admin/collection-exceptions/email";
const states = {
  PENDING: "발송 대기", SENDING: "발송 중", SENT: "발송 요청 접수", DELIVERED: "수신 서버 전달", DELAYED: "전달 지연",
  FAILED: "발송 실패", UNKNOWN: "결과 미확인", BOUNCED: "반송", COMPLAINED: "스팸 신고", SUPPRESSED: "수신 차단", CANCELED: "발송 취소",
} satisfies Record<EmailDeliveryPage["items"][number]["state"], string>;
export const deliveryStates = { ALL: "전체 상태", ...states };
export const deliveryKinds = { ALL: "전체 종류", VERIFICATION: "이메일 인증", POLICY: "정책 알림" };
export const deliveryPeriods = { 1: "최근 24시간", 7: "최근 7일", 30: "최근 30일", 90: "최근 90일" };
export type EmailDeliverySearch = { page?: string | string[]; days?: string | string[]; state?: string | string[]; kind?: string | string[] };
export function emailDeliveryFilters(params: EmailDeliverySearch) {
  return {
    days: typeof params.days === "string" && Object.hasOwn(deliveryPeriods, params.days) ? Number(params.days) : 7,
    state: typeof params.state === "string" && Object.hasOwn(deliveryStates, params.state) ? params.state as keyof typeof deliveryStates : "ALL",
    kind: typeof params.kind === "string" && Object.hasOwn(deliveryKinds, params.kind) ? params.kind as keyof typeof deliveryKinds : "ALL",
  } as const;
}
type Filters = ReturnType<typeof emailDeliveryFilters>;
export function emailDeliveryHref(page: number, filters: Filters) {
  return `${EMAIL_DELIVERIES_PATH}?${new URLSearchParams({ page: String(page), days: String(filters.days), state: filters.state, kind: filters.kind })}`;
}

export function EmailDeliveryFailure({ status, retryHref }: { status: LoadFailure; retryHref: string }) {
  if (status === "unauthenticated" || status === "forbidden") return <CollectionFailure status={status} retryHref={retryHref} />;
  return <PageState kind="error" title={status === "invalid" ? "기간·상태·종류·페이지를 확인해주세요" : "이메일 발송 내역을 불러오지 못했습니다"}
    description="잠시 후 다시 조회해주세요." actions={<a className="button-primary" href={retryHref}>다시 불러오기</a>} />;
}

export function EmailDeliveryList({ data, filters }: { data: EmailDeliveryPage; filters: Filters }) {
  return <>
    <section className="member-panel" aria-label="이메일 발송 설정과 건수">
      <h2>이메일 발송 {data.sendingEnabled ? "켜짐" : "꺼짐"}</h2>
      <p>현재 공급자: {data.provider === "resend" ? "Resend" : "SMTP"}</p>
      {!data.sendingEnabled && <p className="field-help">대기 중인 이메일은 발송하지 않습니다. 이미 발송한 내역은 확인할 수 있습니다.</p>}
      <dl className="exception-facts">
        <dt>기간 내 전체 요청</dt><dd>{data.summary.total}건</dd>
        <dt>실패·반송·신고·차단</dt><dd>{data.summary.failed}건</dd>
        <dt>결과 미확인</dt><dd>{data.summary.unknown}건</dd>
      </dl>
      <p className="field-help">{collectionTime(data.since)} ~ {collectionTime(data.checkedAt)} (서울) · 요약 건수는 상태·종류 필터와 관계없이 집계합니다.</p>
    </section>
    <div className="member-toolbar"><p>조회 결과 {data.total}건 · {data.page}페이지</p>
      <a className="text-link" href={emailDeliveryHref(data.page, filters)}>새로고침</a></div>
    {data.items.length ? <ul className="policy-list email-delivery-list" aria-label="이메일 발송 목록">
      {data.items.map(item => <li className="policy-card" key={item.id}>
        <p className="policy-eyebrow"><span>{states[item.state]}</span><span>{item.provider === "resend" ? "Resend" : item.provider === "smtp" ? "SMTP" : "공급자 기록 없음"}</span></p>
        <h2>{deliveryKinds[item.kind]}</h2>
        <p className="policy-period">요청 {collectionTime(item.createdAt)} (서울)</p>
        {item.state === "UNKNOWN" && <p className="field-help">실제 접수 여부를 확인해야 합니다. 공급자 기록을 확인하기 전에는 다시 보내지 마세요.</p>}
        {item.state === "SENT" && <p className="field-help">발송 요청이 접수됐습니다. 수신 서버 전달 여부는 아직 확인되지 않았습니다.</p>}
        {item.state === "DELIVERED" && <p className="field-help">수신 메일 서버에 전달됐습니다. 사용자의 열람을 뜻하지 않습니다.</p>}
        {item.state === "DELAYED" && <p className="field-help">공급자가 전달을 시도하고 있습니다. 후속 결과를 확인하세요.</p>}
        {["BOUNCED", "COMPLAINED", "SUPPRESSED"].includes(item.state) && <p className="field-help">이 발송에 사용한 주소 설정의 이메일 알림을 중단했습니다.</p>}
        <details className="exception-raw"><summary>발송 기록</summary><dl className="exception-facts">
          <dt>요청 ID</dt><dd>{item.id}</dd>
          <dt>공급자 발송 ID</dt><dd>{item.providerMessageId ?? "기록 없음"}</dd>
          <dt>발송 시작</dt><dd>{collectionTime(item.startedAt)}</dd>
          <dt>처리 완료</dt><dd>{collectionTime(item.finishedAt)}</dd>
          <dt>공급자 이벤트 발생</dt><dd>{collectionTime(item.providerEventAt)}</dd>
        </dl><p className="field-help">모든 시각은 서울 기준입니다. 기록이 없으면 접수·전달 여부를 추정하지 않습니다.</p>
          {item.provider === "resend" && (item.providerMessageId ? <EmailProviderStatus id={item.id} />
            : <p className="field-help">발송 ID가 없어 Resend 상태를 조회할 수 없습니다.</p>)}
        </details>
      </li>)}
    </ul> : <PageState kind="empty" title={data.page > 1 ? "이 페이지에 발송 내역이 없습니다" : "조회 조건에 맞는 발송 내역이 없습니다"}
      description="기간·상태·종류를 바꿔 조회할 수 있습니다."
      actions={<a className="button-secondary" href={data.page > 1 ? emailDeliveryHref(1, filters) : EMAIL_DELIVERIES_PATH}>{data.page > 1 ? "첫 페이지 보기" : "조회 조건 초기화"}</a>} />}
    {(data.page > 1 || data.hasNext) && <nav className="policy-pagination" aria-label="이메일 발송 목록 페이지">
      {data.page > 1 && <a className="button-secondary" href={emailDeliveryHref(data.page - 1, filters)}>이전</a>}
      <span aria-current="page">{data.page}페이지</span>
      {data.hasNext && data.page < 1000 && <a className="button-secondary" href={emailDeliveryHref(data.page + 1, filters)}>다음</a>}
    </nav>}
  </>;
}

import { CollectionNavigation } from "@/features/admin/collection-exception-view";
import { collectionPage, loadEmailDeliveries } from "@/features/admin/load-collection-exceptions";
import { EMAIL_DELIVERIES_PATH, deliveryKinds, deliveryPeriods, deliveryStates, emailDeliveryFilters, emailDeliveryHref,
  EmailDeliveryFailure, EmailDeliveryList, type EmailDeliverySearch } from "@/features/admin/email-deliveries-view";

export const metadata = { title: "이메일 발송 · 청년정책메이트" };

export default async function EmailDeliveriesPage({ searchParams }: { searchParams: Promise<EmailDeliverySearch> }) {
  const params = await searchParams;
  const page = collectionPage(params.page);
  const filters = emailDeliveryFilters(params);
  const result = await loadEmailDeliveries(page, filters.days, filters.state, filters.kind);
  return <>
    <header><p className="page-label">운영 관리</p><h1>이메일 발송</h1><p>발송·전달 상태와 공급자 처리 기록을 확인합니다.</p></header>
    <CollectionNavigation active="email" />
    <form action={EMAIL_DELIVERIES_PATH} className="member-panel rule-review-search email-delivery-search">
      <div className="rule-review-field"><label htmlFor="email-days">요청 기간</label>
        <select id="email-days" name="days" defaultValue={filters.days}>
          {Object.entries(deliveryPeriods).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></div>
      <div className="rule-review-field"><label htmlFor="email-state">발송 상태</label>
        <select id="email-state" name="state" defaultValue={filters.state}>
          {Object.entries(deliveryStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></div>
      <div className="rule-review-field"><label htmlFor="email-kind">메일 종류</label>
        <select id="email-kind" name="kind" defaultValue={filters.kind}>
          {Object.entries(deliveryKinds).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></div>
      <div className="form-actions"><button className="button-primary" type="submit">조회</button>
        <a className="text-link" href={EMAIL_DELIVERIES_PATH}>조회 조건 초기화</a></div>
    </form>
    {result.status === "available" ? <EmailDeliveryList data={result.data} filters={filters} />
      : <EmailDeliveryFailure status={result.status} retryHref={emailDeliveryHref(page, filters)} />}
  </>;
}

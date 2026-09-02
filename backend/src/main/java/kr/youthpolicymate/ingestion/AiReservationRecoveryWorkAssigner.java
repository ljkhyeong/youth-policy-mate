package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Item;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Report;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Ready;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimSkipped;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

// 운영 조회에서 선택한 후보만 받는다. 실제 공급자 확인과 반복 실행은 호출 측 책임이다.
@Service
@Profile("!preview")
public class AiReservationRecoveryWorkAssigner {
    private final AiReservationRecoveryStore recoveryStore;

    public AiReservationRecoveryWorkAssigner(AiReservationRecoveryStore recoveryStore) {
        this.recoveryStore = Objects.requireNonNull(recoveryStore, "AI 예약 복구 저장소가 필요합니다.");
    }

    public ReadyClaimOutcome assign(Report report, Item candidate, Lease lease) {
        Objects.requireNonNull(report, "AI 예약 복구 운영 조회 결과가 필요합니다.");
        Objects.requireNonNull(candidate, "배정할 AI 예약 복구 후보가 필요합니다.");
        Objects.requireNonNull(lease, "AI 예약 복구 임대 정보가 필요합니다.");
        if (!report.items().contains(candidate)) {
            throw new IllegalArgumentException("배정할 복구 후보가 운영 조회 결과에 없습니다.");
        }

        Criteria criteria = report.criteria();
        if (lease.claimedAt().isBefore(criteria.evaluatedAt())) {
            throw new IllegalArgumentException("복구 소유권 획득은 운영 조회 판단보다 빠를 수 없습니다.");
        }
        if (!(candidate.decision() instanceof Ready)) {
            return new ReadyClaimSkipped(candidate.decision());
        }
        return recoveryStore.claimIfReady(
                candidate.reservation().reservationId(), criteria.schedule(), lease);
    }
}

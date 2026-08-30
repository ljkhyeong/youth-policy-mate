package kr.youthpolicymate.eligibility;

import java.util.Objects;

public sealed interface IncomeAnswer {

    IncomeBasis basis();

    // 호출 측에서 단위를 확인한 원화 금액만 전달한다. 원천 숫자를 그대로 받는 API 입력 모델이 아니다.
    record KnownRange(IncomeBasis basis, IncomeRange range) implements IncomeAnswer {
        public KnownRange {
            basis = Objects.requireNonNull(basis, "소득 답변의 비교 기준이 필요합니다.");
            range = Objects.requireNonNull(range, "확인한 원화 소득 구간이 필요합니다.");
        }
    }

    record Unknown(IncomeBasis basis) implements IncomeAnswer {
        public Unknown {
            basis = Objects.requireNonNull(basis, "모름으로 답한 소득 비교 기준이 필요합니다.");
        }
    }
}

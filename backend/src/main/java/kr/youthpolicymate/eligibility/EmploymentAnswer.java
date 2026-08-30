package kr.youthpolicymate.eligibility;

import java.util.Objects;

public record EmploymentAnswer(EmploymentFact fact, Response response) {

    public EmploymentAnswer {
        fact = Objects.requireNonNull(fact, "어떤 취업 사실에 대한 답변인지 확인할 정보가 필요합니다.");
        response = Objects.requireNonNull(response, "취업 사실에 대한 답변이 필요합니다.");
    }

    public enum Response {
        APPLIES("해당함"),
        DOES_NOT_APPLY("해당하지 않음"),
        UNKNOWN("모름");

        private final String label;

        Response(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}

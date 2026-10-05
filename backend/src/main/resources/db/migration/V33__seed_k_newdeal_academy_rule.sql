-- 공식 안내(고용24 K-뉴딜 아카데미, 2026-07-22 수정)와 온통청년 원문을 대조해 검토한 규칙.
-- 운영자가 이미 이 정책의 규칙을 적용했다면 현재 적용 버전을 바꾸지 않는다.
INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8033-000000000001', '20260714005400113258', 'k-newdeal-academy-2026-v1', $rule$
{
  "policyNumber": "20260714005400113258",
  "ruleVersion": "k-newdeal-academy-2026-v1",
  "scope": "2026년 K-뉴딜 아카데미 참여 조건",
  "reason": "참여 신청일 기준 연령·취업·사업자등록·재학·다른 직업훈련 수강과 아카데미 시작 연도의 참여 횟수를 확인해요. 조건이 맞아도 아카데미별 선발 절차가 있어요.",
  "sourceUrl": "https://www.work24.go.kr/cm/c/f/1100/selecSystInfo.do?currentPageNo=1&recordCountPerPage=10&systId=SI00000512&systClId=SC00000391",
  "questions": [
    {
      "id": "age",
      "label": "참여 신청일 기준 만 나이는 어떻게 되나요?",
      "help": "만 15~34세 미취업 청년이 대상이에요. 군필자는 의무복무 기간만큼 연령 상한을 높여 최대 만 39세까지 참여할 수 있어요.",
      "options": [
        {
          "value": "AGE_15_TO_34",
          "label": "만 15~34세"
        },
        {
          "value": "EXTENSION_CONFIRMED",
          "label": "만 35~39세 · 34세에 의무복무 기간을 더한 상한 이내"
        },
        {
          "value": "EXTENSION_PENDING",
          "label": "만 35세 이상 · 군복무 연장 확인 중"
        },
        {
          "value": "OVER_LIMIT",
          "label": "만 35세 이상 · 연장 해당 없음 또는 만 40세 이상"
        },
        {
          "value": "UNDER_15",
          "label": "만 15세 미만"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "employment",
      "label": "참여 신청일에 고용보험에 가입해 일하고 있나요?",
      "help": "취업 여부는 신청일의 고용보험 가입으로 확인해요. 주 30시간 미만 단시간·플랫폼·일용 근로자가 훈련시간 외 근로임을 증빙하거나, 아카데미 시작 전에 제한 사유가 해소되면 참여할 수 있어요. 사업자등록은 다음 질문에서 확인해요.",
      "options": [
        {
          "value": "NOT_INSURED",
          "label": "고용보험에 가입돼 있지 않아요"
        },
        {
          "value": "EXCEPTION_CONFIRMED",
          "label": "가입 중 · 근로 예외 또는 시작 전 해소 인정 확인"
        },
        {
          "value": "EMPLOYED_NO_EXCEPTION",
          "label": "가입 중 · 예외 없고 시작 전에도 해소 안 됨"
        },
        {
          "value": "UNKNOWN",
          "label": "가입 여부·예외 확인 중"
        }
      ]
    },
    {
      "id": "business",
      "label": "참여 신청일에 사업자등록이 있나요?",
      "help": "사업자등록이 있어도 실제 사업을 하지 않음을 객관적으로 증명하거나, 아카데미 시작 전에 제한 사유가 해소되면 참여할 수 있어요.",
      "options": [
        {
          "value": "NONE",
          "label": "사업자등록 없음"
        },
        {
          "value": "INACTIVE_CONFIRMED",
          "label": "등록 있음 · 미영업 증빙 또는 시작 전 해소 인정 확인"
        },
        {
          "value": "ACTIVE_NO_EXCEPTION",
          "label": "등록 있음 · 예외 없고 시작 전에도 해소 안 됨"
        },
        {
          "value": "UNKNOWN",
          "label": "사업자등록·예외 확인 중"
        }
      ]
    },
    {
      "id": "education",
      "label": "고등학교나 대학(원)에 재학 중인가요?",
      "help": "재학생은 참여가 제한돼요. 대학(원) 졸업예정자(4학년 2학기 이상·마지막 학기), 졸업 이수학점을 모두 취득한 사람, 휴학생, 특성화고·산업수요맞춤형고·일반고 직업교육 관련 학과 3학년 2학기로 학교장 추천을 받은 졸업예정자는 예외예요. 아카데미 시작 전에 졸업 등으로 제한 사유가 해소되면 증빙 후 참여할 수 있어요. 휴학 예외의 범위는 안내마다 달라 1년 미만 휴학은 확인이 필요해요.",
      "options": [
        {
          "value": "NOT_ENROLLED",
          "label": "재학·휴학 중이 아니에요"
        },
        {
          "value": "GRADUATING",
          "label": "대학(원) 졸업예정 또는 졸업 이수학점 모두 취득"
        },
        {
          "value": "LONG_LEAVE",
          "label": "대학(원) 휴학 · 연속 1년 이상 또는 통산 2년 이상"
        },
        {
          "value": "SHORT_LEAVE",
          "label": "대학(원) 휴학 · 위 기간 미만"
        },
        {
          "value": "VOCATIONAL_RECOMMENDED",
          "label": "고등학교 직업교육 학과 3학년 2학기 · 학교장 추천"
        },
        {
          "value": "RESOLVING_BEFORE_START",
          "label": "재학 중 · 시작 전 해소 예정"
        },
        {
          "value": "ENROLLED_NO_EXCEPTION",
          "label": "재학 중 · 예외 없고 시작 전에도 해소 안 됨"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "training",
      "label": "국가나 지방자치단체가 실시하거나 비용을 지원하는 직업훈련을 지금 수강하고 있나요?",
      "help": "정부·지자체가 실시하거나 비용을 지원하는 직업능력개발훈련을 수강 중이면 참여가 제한돼요. 아카데미 시작 전에 수강이 끝나면 증빙 후 참여할 수 있어요. 수강 중인 과정이 해당하는지 모르면 운영기관에 확인해주세요.",
      "options": [
        {
          "value": "NO",
          "label": "수강하고 있지 않아요"
        },
        {
          "value": "ENDS_BEFORE_START",
          "label": "수강 중 · 시작 전 종료 예정"
        },
        {
          "value": "CONTINUES",
          "label": "수강 중 · 시작 후에도 계속"
        },
        {
          "value": "UNKNOWN",
          "label": "해당 여부 확인 중"
        }
      ]
    },
    {
      "id": "participation",
      "label": "참여하려는 아카데미가 시작하는 해에 K-뉴딜 아카데미에 참여한 적이 있나요?",
      "help": "아카데미 시작일이 속한 연도에 최대 2회까지 참여할 수 있어요. 여러 아카데미에 동시에 참여하거나 같은 아카데미에 다시 참여할 수는 없어요.",
      "options": [
        {
          "value": "NONE_OR_ONCE",
          "label": "그해 0~1회 · 일정이 겹치는 아카데미 없음"
        },
        {
          "value": "TWICE",
          "label": "그해 이미 2회 참여"
        },
        {
          "value": "OVERLAPPING",
          "label": "참여 중인 아카데미와 일정이 겹쳐요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "02369c6645f3cde944eb6186852b0a5c33c160e212ca6e0c01ccdd16e427f2b2",
  "validFrom": "2026-07-06T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "K-뉴딜 아카데미 공통 참여 조건의 확인 결과예요. 아카데미별 선발과 참여수당 요건은 별도로 확인해주세요.",
  "remainingChecks": [
    "고용24 등 직업안정기관에 구직등록이 필요해요. 아직이라면 아카데미 시작 전까지 등록하고 증빙해주세요.",
    "대한민국 국적이 없으면 참여할 수 없어요. 같은 아카데미에는 다시 참여할 수 없어요.",
    "근로·사업자등록·재학 예외는 증빙서류 제출과 운영기관의 인정이 필요해요.",
    "아카데미별 모집 일정·선발 기준과 참여수당 지급 요건은 고용24의 연계 기업 모집 페이지에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "참여 신청일 연령",
      "evidence": "참여 신청일 기준 만 15~34세가 대상이에요. 군필자는 의무복무 기간만큼 상한을 높여 최대 만 39세까지 적용해요.",
      "questionId": "age",
      "cases": [
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "OVER_LIMIT",
              "UNDER_15"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "만 35~39세는 군필자의 의무복무 기간만큼 연장될 수 있어요. 만 40세 이상은 참여할 수 없어요. 군복무 기간과 참여 신청일의 만 나이를 확인해주세요."
    },
    {
      "label": "취업 상태",
      "evidence": "참여 신청일 현재 고용보험 피보험자격으로 취업 여부를 확인해요. 주 30시간 미만 단시간·고용보험 가입 대상 플랫폼·일용 근로자가 훈련시간 외 근로임을 증빙하거나 아카데미 개시 전 제한 사유가 해소되면 참여할 수 있어요.",
      "questionId": "employment",
      "cases": [
        {
          "when": {
            "employment": [
              "NOT_INSURED",
              "EXCEPTION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "employment": [
              "EMPLOYED_NO_EXCEPTION"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "고용보험 가입 여부와 근로 예외의 증빙 인정 여부를 운영기관에서 확인해주세요."
    },
    {
      "label": "사업자등록",
      "evidence": "사업자등록 중인 사람은 참여가 제한돼요. 실제 사업을 영위하지 않음을 객관적으로 증명하거나 아카데미 개시 전 제한 사유가 해소되면 참여할 수 있어요.",
      "questionId": "business",
      "cases": [
        {
          "when": {
            "business": [
              "NONE",
              "INACTIVE_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "business": [
              "ACTIVE_NO_EXCEPTION"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "사업자등록 여부와 미영업 증빙의 인정 여부를 운영기관에서 확인해주세요."
    },
    {
      "label": "재학 상태",
      "evidence": "고등학교·대학(원) 재학생은 참여가 제한돼요. 대학(원) 졸업예정자, 졸업 이수학점 취득자, 휴학생, 고등학교 직업교육 관련 학과의 학교장 추천 졸업예정자는 참여할 수 있어요. 제한 사유가 아카데미 개시 전에 해소되면 증빙 후 참여할 수 있어요. 휴학 예외는 안내마다 범위가 달라요(신청일 현재 휴학 중 또는 연속 1년 이상·통산 2년 이상 장기 휴학).",
      "questionId": "education",
      "cases": [
        {
          "when": {
            "education": [
              "NOT_ENROLLED",
              "GRADUATING",
              "LONG_LEAVE",
              "VOCATIONAL_RECOMMENDED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "education": [
              "ENROLLED_NO_EXCEPTION"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "재학 예외나 시작 전 해소에 해당하는지 확인해주세요. 1년 미만 휴학은 안내마다 예외 범위가 달라 운영기관 확인이 필요해요."
    },
    {
      "label": "다른 직업훈련 수강",
      "evidence": "국가 또는 지방자치단체가 실시하거나 비용을 지원하는 직업능력개발훈련을 수강하고 있는 사람은 참여가 제한돼요. 아카데미 개시 전에 해소되면 증빙 후 참여할 수 있어요.",
      "questionId": "training",
      "cases": [
        {
          "when": {
            "training": [
              "NO"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "training": [
              "CONTINUES"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "수강 중인 과정이 국가·지자체 직업훈련에 해당하는지, 아카데미 시작 전에 끝나는지 증빙과 함께 확인해주세요."
    },
    {
      "label": "개시 연도 참여 횟수",
      "evidence": "아카데미 개시일 기준 연도에 최대 2회까지 참여할 수 있고, 동시에 여러 아카데미에 참여하거나 같은 아카데미에 다시 참여할 수 없어요.",
      "questionId": "participation",
      "cases": [
        {
          "when": {
            "participation": [
              "NONE_OR_ONCE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "participation": [
              "TWICE",
              "OVERLAPPING"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "아카데미가 시작하는 해의 참여 횟수와 일정이 겹치는 아카데미가 있는지 확인해주세요."
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 15,
    "maximumInclusive": 34,
    "referenceDate": null,
    "below": "UNDER_15",
    "within": "AGE_15_TO_34",
    "above": "EXTENSION_PENDING",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 참여 신청 시 연령이에요. 신청일이 달라지면 다시 확인해주세요."
}
$rule$::jsonb, 'migration-v33', 'K-뉴딜 아카데미 공통 참여 조건 검토', now(), 'migration-v33')
ON CONFLICT DO NOTHING;
INSERT INTO policy_rule_heads(policy_number, version_id)
SELECT '20260714005400113258', 'ba390000-0000-4000-8033-000000000001' WHERE EXISTS (SELECT 1 FROM policy_rule_versions WHERE id = 'ba390000-0000-4000-8033-000000000001')
ON CONFLICT (policy_number) DO NOTHING;

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000001', '20260710005400113257', 'k-pass-2026-v1', $rule$
{
  "policyNumber": "20260710005400113257",
  "ruleVersion": "k-pass-2026-v1",
  "scope": "K-패스(모두의카드) 가입·이용 조건",
  "reason": "이번 달의 연령·가입·주소지 확인·이용 횟수를 확인해요. 이용 내역이 늘면 결과가 달라질 수 있어요. 환급률과 금액은 K-패스에서 확인해주세요.",
  "sourceUrl": "https://korea-pass.kr/info/use_pay.do",
  "questions": [
    {
      "id": "age",
      "label": "현재 만 19세 이상인가요?",
      "help": "기본 가입 연령을 확인해요. 만 35세 이상도 기본 가입 대상이므로 청년 환급률의 연령 범위와 구분해요. 생년월일 전체는 입력하지 않아요.",
      "options": [
        {
          "value": "ADULT",
          "label": "만 19세 이상이에요"
        },
        {
          "value": "UNDER_19",
          "label": "만 19세 미만이에요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "registration",
      "label": "공식 홈페이지나 앱에 회원가입하고 이용 중인 카드를 등록했나요?",
      "help": "카드 발급만으로는 적립되지 않아요. 모두의카드(K-패스) 회원가입과 실제 이용 카드의 등록 상태를 확인해주세요. 카드번호는 입력하지 않아요.",
      "options": [
        {
          "value": "REGISTERED",
          "label": "회원가입·카드 등록 완료"
        },
        {
          "value": "CARD_ONLY",
          "label": "카드만 발급 · 회원가입·등록 전"
        },
        {
          "value": "NOT_REGISTERED",
          "label": "회원가입 또는 카드 등록 미완료"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "residence",
      "label": "공식 서비스에서 참여 지자체의 주민으로 확인됐나요?",
      "help": "서울을 포함한 참여 지자체 거주를 확인해요. 외국인도 가입할 수 있으며, 이 서비스에서는 국적·주소·증빙서류를 받지 않아요. 주소지 확인이 진행 중이면 그대로 선택해주세요.",
      "options": [
        {
          "value": "CONFIRMED",
          "label": "참여 지자체 거주 확인 완료"
        },
        {
          "value": "PENDING",
          "label": "주소지 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "monthlyRides",
      "label": "이번 달의 인정 이용 횟수와 첫 가입 월 여부를 확인했나요?",
      "help": "화면에 표시된 연월의 공식 이용내역으로 답해주세요. 환승마다 따로 세거나 미반영 내역을 0회로 바꾸지 않아요. 이번 달 가입 여부가 불확실하면 ‘아직 확인하지 못했어요’를 선택해주세요.",
      "options": [
        {
          "value": "AT_LEAST_15",
          "label": "15회 이상"
        },
        {
          "value": "FIRST_MONTH_1_TO_14",
          "label": "가입 첫 달 · 1~14회"
        },
        {
          "value": "LATER_MONTH_1_TO_14",
          "label": "가입 첫 달 아님 · 1~14회"
        },
        {
          "value": "ZERO",
          "label": "0회 · 반영 대기 내역 없음"
        },
        {
          "value": "PENDING",
          "label": "이용내역 반영 대기 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "8285137b93b842eec33bd8f9ad79f8eef1c953858ebc9feca37e90140644e653",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "이번 달 가입·이용 조건의 확인 결과예요. 실제 적립 내역과 환급액·지급 여부는 K-패스에서 확인해주세요.",
  "remainingChecks": [
    "공식 서비스의 본인·주소지·카드 등록 결과와 실제 적립 대상 이용내역을 확인해주세요. 이 서비스는 해당 계정이나 카드 이용내역을 조회하지 않아요.",
    "시외·고속·공항버스, KTX·SRT 등 별도 발권 수단은 적립 대상에서 제외돼요. 공항철도와 공항버스를 구분하고 실제 인정 내역은 공식 서비스에서 확인해주세요.",
    "청년·다자녀·저소득 등 대상 구분, 지자체 추가 혜택, 한시 혜택과 환급 방식에 따라 금액이 달라져요. 환급률과 금액은 K-패스에서 확인해주세요.",
    "이번 달 이용내역이 늘면 다시 비교해주세요. 최종 환급액과 지급일·지급 방식은 K-패스와 카드사에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "기본 가입 연령",
      "evidence": "오늘(서울) 기준 기본 가입 연령은 만 19세 이상이에요. 만 35세 이상도 가입할 수 있고, 청년 환급률은 만 19~34세에 적용돼요. 근거: https://korea-pass.kr/info/use_join.do",
      "questionId": "age",
      "cases": [
        {
          "when": {
            "age": [
              "ADULT"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "UNDER_19"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요. 연령이나 가입·등록 상태가 바뀌면 다시 확인해주세요."
        }
      ],
      "unknownExplanation": "이 항목은 아직 확인할 수 없어요. 공식 안내와 본인 정보를 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "회원가입과 이용 카드 등록",
      "evidence": "적립을 받으려면 카드 발급 후 공식 홈페이지나 앱에 회원가입하고 카드를 등록해야 해요. 가입하지 않고 이용한 카드에는 환급금이 발생하지 않는다고 안내돼 있어요. 근거: https://korea-pass.kr/info/use_accm.do",
      "questionId": "registration",
      "cases": [
        {
          "when": {
            "registration": [
              "REGISTERED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "registration": [
              "CARD_ONLY",
              "NOT_REGISTERED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요. 연령이나 가입·등록 상태가 바뀌면 다시 확인해주세요."
        }
      ],
      "unknownExplanation": "이 항목은 아직 확인할 수 없어요. 공식 안내와 본인 정보를 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "참여 지자체 거주 확인",
      "evidence": "참여 지자체의 주민이 가입 대상이며 외국인도 가입할 수 있어요. 주소지 확인이 끝나야 거주 조건을 확인할 수 있어요. 근거: https://korea-pass.kr/info/use_join.do",
      "questionId": "residence",
      "cases": [
        {
          "when": {
            "residence": [
              "CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "residence": [
              "PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "K-패스에서 주소지 확인이나 이용내역 반영이 끝나면 다시 답해주세요."
        }
      ],
      "unknownExplanation": "이 항목은 아직 확인할 수 없어요. 공식 안내와 본인 정보를 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "이번 달 인정 이용 횟수",
      "evidence": "매월 1일부터 말일까지 인정 이용을 비교하며 보통 15회가 필요해요. 가입 첫 달에는 15회 미만 이용에도 예외가 있어요. 환승을 각각 별도 횟수로 세지 않아요. 근거: https://korea-pass.kr/info/use_pay.do",
      "questionId": "monthlyRides",
      "cases": [
        {
          "when": {
            "monthlyRides": [
              "AT_LEAST_15"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "monthlyRides": [
              "FIRST_MONTH_1_TO_14"
            ]
          },
          "outcome": "MET",
          "explanation": "가입 첫 달에는 15회 미만도 인정돼요. 다음 달부터는 이용 횟수 기준을 다시 확인해주세요."
        },
        {
          "when": {
            "monthlyRides": [
              "LATER_MONTH_1_TO_14",
              "ZERO"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "현재 이용 횟수는 기준에 못 미쳐요. 월말까지 이용 내역이 늘면 결과가 달라질 수 있어요."
        },
        {
          "when": {
            "monthlyRides": [
              "PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "K-패스에서 주소지 확인이나 이용내역 반영이 끝나면 다시 답해주세요."
        }
      ],
      "unknownExplanation": "이 항목은 아직 확인할 수 없어요. 공식 안내와 본인 정보를 확인한 뒤 다시 답해주세요."
    }
  ],
  "monthly": true,
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 19,
    "maximumInclusive": null,
    "referenceDate": null,
    "below": "UNDER_19",
    "within": "ADULT",
    "above": null,
    "showCalculatedAge": false
  },
  "ageNotice": ""
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260710005400113257', 'ba390000-0000-4000-8024-000000000001');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000002', '20260616005400113238', 'youth-housing-2026-v1', $rule$
{
  "policyNumber": "20260616005400113238",
  "ruleVersion": "youth-housing-2026-v1",
  "scope": "2026년 청년주택드림청약통장 가입 조건",
  "reason": "가입 시 연령·본인 무주택·소득을 확인해요. 기존 통장 전환과 우대금리·비과세·대출은 은행에서 별도로 확인해주세요.",
  "sourceUrl": "https://obank.kbstar.com/quics?cc=b061761%3Ab061770&isNew=N&page=C020702&prcode=DP01000935",
  "questions": [
    {
      "id": "age",
      "label": "가입일 기준 만 나이는 어떻게 되나요?",
      "help": "만 35세 이상은 인정되는 병역기간을 최대 6년 차감할 수 있어요. 차감 후 만 34세 이하인지 은행에서 확인해주세요.",
      "options": [
        {
          "value": "AGE_19_TO_34",
          "label": "만 19~34세"
        },
        {
          "value": "MILITARY_AGE_CONFIRMED",
          "label": "은행 확인 · 차감 후 만 34세 이하"
        },
        {
          "value": "UNDER_19",
          "label": "만 19세 미만"
        },
        {
          "value": "NO_MILITARY_DEDUCTION",
          "label": "만 35세 이상 · 차감 대상 아님"
        },
        {
          "value": "OVER_LIMIT_CONFIRMED",
          "label": "차감 후에도 만 35세 이상"
        },
        {
          "value": "MILITARY_AGE_PENDING",
          "label": "병역기간 차감 기준 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "homeOwnership",
      "label": "가입일에 본인 소유의 주택이 없나요?",
      "help": "가입 조건은 본인의 무주택 여부예요. 소유 여부가 불분명하면 은행에서 확인해주세요.",
      "options": [
        {
          "value": "NO_HOME",
          "label": "본인 소유의 주택이 없어요"
        },
        {
          "value": "OWNS_HOME",
          "label": "본인 소유의 주택이 있어요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "incomeBasis",
      "label": "가입용 소득서류의 기준을 확인했나요?",
      "help": "원칙은 직전 과세연도인 2025년이에요. 소득이 아직 확정되지 않았거나 첫 취업·군복무 예외에 해당하면 은행에서 적용 기준을 확인해주세요.",
      "options": [
        {
          "value": "PREVIOUS_YEAR",
          "label": "2025년 신고소득 서류 확인"
        },
        {
          "value": "EARLIER_YEAR_CONFIRMED",
          "label": "은행 확인 · 2024년 소득 적용"
        },
        {
          "value": "ANNUALIZED_CONFIRMED",
          "label": "은행 확인 · 첫 취업 소득을 연소득으로 환산"
        },
        {
          "value": "MILITARY_CONFIRMED",
          "label": "은행 확인 · 군복무자 소득 예외"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "incomeAmount",
      "label": "가입용 서류에서 확인한 연소득은 얼마인가요?",
      "help": "근로소득은 총급여액, 사업·기타소득은 종합소득금액 기준이에요. 월급·매출·가구소득을 입력하지 마세요. 군복무자 예외는 비과세 소득만 있는 경우예요.",
      "options": [
        {
          "value": "UP_TO_50M",
          "label": "연 5,000만 원 이하"
        },
        {
          "value": "OVER_50M",
          "label": "연 5,000만 원 초과"
        },
        {
          "value": "TAX_EXEMPT_ONLY",
          "label": "군복무 급여 등 비과세 소득만"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "52ab8f797a2bd470745ded339b0b789ea6baf46e44177f4699e6d60fffc8d214",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "연령·본인 무주택·소득의 확인 결과예요. 가입 서류와 기존 통장 전환 여부는 은행에서 확인해주세요.",
  "remainingChecks": [
    "국내 거주자 해당 여부와 나이·무주택·소득 증빙서류를 은행에서 확인해주세요.",
    "주택청약 계좌는 전 금융기관을 합쳐 1인 1계좌예요. 기존 통장이 있다면 신규 가입·전환 여부를 은행에서 확인해주세요.",
    "우대금리·비과세·소득공제는 각각 별도 조건을 확인해야 해요.",
    "청약 자격과 연계 대출의 신청 조건·심사는 별도로 확인해주세요."
  ],
  "checks": [
    {
      "label": "가입일 연령",
      "evidence": "만 19~34세가 대상이에요. 만 35세 이상은 인정되는 병역기간을 최대 6년 차감한 나이가 만 34세 이하여야 해요.",
      "questionId": "age",
      "cases": [
        {
          "when": {
            "age": [
              "AGE_19_TO_34",
              "MILITARY_AGE_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "NO_MILITARY_DEDUCTION",
              "OVER_LIMIT_CONFIRMED",
              "UNDER_19"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "가입일의 만 나이와 병역기간 차감 기준을 확인해주세요."
    },
    {
      "label": "본인 무주택",
      "evidence": "가입일 기준 본인 소유의 주택이 없어야 해요. 세대주 여부와 세대원의 무주택 요건은 비과세 등 별도 기준에서 확인해요.",
      "questionId": "homeOwnership",
      "cases": [
        {
          "when": {
            "homeOwnership": [
              "NO_HOME"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "homeOwnership": [
              "OWNS_HOME"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "가입일에 본인 소유의 주택이 없는지 확인해주세요."
    },
    {
      "label": "가입 소득 기준",
      "evidence": "총급여액 또는 종합소득금액이 연 5,000만 원 이하여야 해요. 직전 연도 미확정·첫 취업은 서류 기준을 따르며, 군복무자는 비과세 소득만 있는 경우 복무 기간 등 예외 조건을 확인해요.",
      "questionId": "incomeBasis",
      "cases": [
        {
          "when": {
            "incomeBasis": [
              "ANNUALIZED_CONFIRMED",
              "EARLIER_YEAR_CONFIRMED",
              "PREVIOUS_YEAR"
            ],
            "incomeAmount": [
              "UP_TO_50M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "incomeBasis": [
              "MILITARY_CONFIRMED"
            ],
            "incomeAmount": [
              "TAX_EXEMPT_ONLY"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "incomeBasis": [
              "ANNUALIZED_CONFIRMED",
              "EARLIER_YEAR_CONFIRMED",
              "PREVIOUS_YEAR"
            ],
            "incomeAmount": [
              "OVER_50M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "incomeBasis": [
              "MILITARY_CONFIRMED"
            ],
            "incomeAmount": [
              "",
              "OVER_50M",
              "UNKNOWN",
              "UP_TO_50M"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "군복무자 소득 예외는 비과세 소득만 있는 경우예요. 소득 종류와 적용 기간을 은행에서 다시 확인해주세요."
        }
      ],
      "unknownExplanation": "가입용 소득서류의 기준 연도와 금액을 확인해주세요. 첫 취업·군복무 예외는 은행에서 확인해야 해요.",
      "providedAnswers": [
        {
          "questionId": "incomeBasis",
          "prefix": ""
        },
        {
          "questionId": "incomeAmount",
          "prefix": ""
        }
      ],
      "separator": " / "
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 19,
    "maximumInclusive": 34,
    "referenceDate": null,
    "below": "UNDER_19",
    "within": "AGE_19_TO_34",
    "above": "MILITARY_AGE_PENDING",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 가입할 때의 연령이에요. 가입일이 달라지면 다시 확인해주세요."
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260616005400113238', 'ba390000-0000-4000-8024-000000000002');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000003', '20260520005400213208', 'seoul-network-2026-h2-v1', $rule$
{
  "policyNumber": "20260520005400213208",
  "ruleVersion": "seoul-network-2026-h2-v1",
  "scope": "2026년 하반기 서울청년정책네트워크 참여 조건",
  "reason": "이 공고의 연령·서울 거주 또는 생활권·위원 이력을 확인해요.",
  "sourceUrl": "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605150002",
  "questions": [
    {
      "id": "birthRange",
      "label": "공고의 출생일 범위에 해당하나요?",
      "help": "기본 대상은 1986.1.2.~2007.1.1. 출생자예요(양 끝 날짜 포함). 의무복무 제대군인은 최대 3세까지 연령 상한을 연장해요. 연장 후 기준 충족 여부는 담당 기관에서 확인해주세요.",
      "options": [
        {
          "value": "BASE_RANGE",
          "label": "1986.1.2.~2007.1.1. 출생"
        },
        {
          "value": "MILITARY_EXTENSION_CONFIRMED",
          "label": "기관 확인 · 연장된 연령 기준 충족"
        },
        {
          "value": "TOO_YOUNG",
          "label": "2007.1.2. 이후 출생"
        },
        {
          "value": "OLDER_NO_EXTENSION",
          "label": "1986.1.1.까지 출생 · 연장 불가"
        },
        {
          "value": "EXTENDED_LIMIT_EXCEEDED",
          "label": "연장 후에도 연령 상한 초과"
        },
        {
          "value": "MILITARY_EXTENSION_PENDING",
          "label": "군복무에 따른 연령 연장 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "seoulConnection",
      "label": "신청 당시 서울 거주·재학·재직 중이었나요?",
      "help": "서울 거주·대학·직장 중 하나에 해당하면 돼요. 대학 재·휴학과 직장 재·휴직을 포함해요. 사업자는 서울 생활권 증빙을 담당 기관에서 확인해주세요.",
      "options": [
        {
          "value": "RESIDENT",
          "label": "서울 거주"
        },
        {
          "value": "UNIVERSITY",
          "label": "서울 소재 대학 재학·휴학"
        },
        {
          "value": "WORKPLACE",
          "label": "서울 소재 직장 재직·휴직"
        },
        {
          "value": "BUSINESS_CONFIRMED",
          "label": "서울 사업장 · 기관에서 증빙 인정"
        },
        {
          "value": "NONE_CONFIRMED",
          "label": "모두 해당 없음 · 기관 확인"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "consecutiveTerms",
      "label": "2023~2025년 청정넷 위원으로 연속 활동했나요?",
      "help": "공고는 2023~2025년 2차례 연임자를 제외해요. 중간 공백 등으로 연임 여부가 불분명하면 담당 기관에서 확인해주세요.",
      "options": [
        {
          "value": "NOT_APPLICABLE",
          "label": "해당 없음 (첫 참여 포함)"
        },
        {
          "value": "APPLIES",
          "label": "해당함 · 2차례 연임"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "priorDisqualification",
      "label": "과거 청정넷 활동에서 위촉 제한 사유가 있나요?",
      "help": "공고는 과거 위원 활동 중 징계·해촉 등의 전력이 있으면 위촉할 수 없다고 안내해요. 해당 여부가 불분명하면 담당 기관에서 확인해주세요.",
      "options": [
        {
          "value": "NONE",
          "label": "없음 (첫 참여 포함)"
        },
        {
          "value": "CONFIRMED",
          "label": "있음 · 담당 기관 확인"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "f5ae512cf9721607bb849c8d466db4b21e17eb2c8b84eb8dda013fa158d98ca7",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "연령·서울 거주 또는 생활권·위원 이력만 비교한 결과예요.",
  "remainingChecks": [
    "신청서 3개 문항 총 500자 이상 작성과 거주·생활 증빙서류 제출 여부를 확인해야 해요.",
    "사전교육 이수 인증과 퀴즈 70점 이상 득점 여부는 별도 확인이 필요해요.",
    "우선선발은 별도 증빙으로 확인해요. 해당 서류를 내지 않으면 일반선발로 전환돼요.",
    "서류 적격 여부와 종합 평가에 따라 선발해요. 실제 선발 결과는 공식 발표에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "공고의 연령 기준",
      "evidence": "2026.1.1. 기준 만 19~39세로, 1986.1.2.~2007.1.1. 출생자가 기본 대상이에요. 의무복무 제대군인의 연령 상한 연장은 최대 3세 범위에서 확인해요.",
      "questionId": "birthRange",
      "cases": [
        {
          "when": {
            "birthRange": [
              "BASE_RANGE",
              "MILITARY_EXTENSION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "birthRange": [
              "EXTENDED_LIMIT_EXCEEDED",
              "OLDER_NO_EXTENSION",
              "TOO_YOUNG"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "출생일 범위와 군복무에 따른 연령 연장 후 기준 충족 여부를 확인해주세요."
    },
    {
      "label": "서울 거주 또는 생활권",
      "evidence": "서울 거주자 또는 서울 소재 대학의 재·휴학생, 직장의 재·휴직자가 대상이에요. 서울 생활권 증빙은 재학·휴학·재직증명서, 사업자등록증 등으로 확인해요.",
      "questionId": "seoulConnection",
      "cases": [
        {
          "when": {
            "seoulConnection": [
              "BUSINESS_CONFIRMED",
              "RESIDENT",
              "UNIVERSITY",
              "WORKPLACE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "seoulConnection": [
              "NONE_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "서울에 살지 않아도 서울 소재 대학·직장에 해당할 수 있어요. 서울 생활권 증빙을 확인해주세요."
    },
    {
      "label": "연임 제한",
      "evidence": "2023~2025년 2차례 연임한 위원은 이 모집에서 제외돼요.",
      "questionId": "consecutiveTerms",
      "cases": [
        {
          "when": {
            "consecutiveTerms": [
              "NOT_APPLICABLE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "consecutiveTerms": [
              "APPLIES"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "2023~2025년 위원 활동 이력과 연임 여부를 확인해주세요."
    },
    {
      "label": "과거 위원 활동에 따른 제한",
      "evidence": "과거 위원 활동 중 징계·해촉 등의 전력이 있으면 위촉할 수 없어요.",
      "questionId": "priorDisqualification",
      "cases": [
        {
          "when": {
            "priorDisqualification": [
              "NONE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "priorDisqualification": [
              "CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "위촉 제한 사유에 해당하는지 담당 기관에서 확인해주세요."
    }
  ],
  "periodNotice": {
    "opensAt": "2026-05-20T00:00:00Z",
    "closesAt": "2026-05-29T08:00:00Z",
    "before": "접수 전이에요. 접수 기간은 2026년 5월 20일 09:00~5월 29일 17:00(서울)이에요.",
    "open": "접수 기간은 2026년 5월 20일 09:00~5월 29일 17:00(서울)이에요.",
    "closed": "이 모집은 2026년 5월 29일 17:00(서울)에 접수가 마감됐어요."
  },
  "birthBinding": {
    "questionId": "birthRange",
    "minimumInclusive": "1986-01-02",
    "maximumInclusive": "2007-01-01",
    "below": "MILITARY_EXTENSION_PENDING",
    "within": "BASE_RANGE",
    "above": "TOO_YOUNG"
  }
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260520005400213208', 'ba390000-0000-4000-8024-000000000003');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000004', '20260614005400213232', 'moving-fee-2026-h1-v4', $rule$
{
  "policyNumber": "20260614005400213232",
  "ruleVersion": "moving-fee-2026-h1-v4",
  "scope": "2026년 상반기 서울 청년 중개보수·이사비 지원 조건",
  "reason": "연령·이사·계약·주택·소득·중복지원과 공고의 참여 제한을 확인해요. 증빙과 선발 심사 등은 별도 확인이 필요해요.",
  "sourceUrl": "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2604010002",
  "questions": [
    {
      "id": "birthRange",
      "label": "1986.1.1.~2007.12.31. 출생인가요?",
      "help": "공고에 명시된 출생일 범위예요. 양 끝 날짜를 포함하며, 오늘의 만 나이로 계산하지 않아요.",
      "options": [
        {
          "value": "IN_RANGE",
          "label": "해당해요"
        },
        {
          "value": "OUTSIDE",
          "label": "해당하지 않아요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "move",
      "label": "이사와 전입신고가 공고의 기간에 해당하나요?",
      "help": "2024.1.1. 이후 서울로 전입하거나 서울 안에서 이사하고, 2026.4.14. 신청 마감까지 전입신고를 마쳐야 해요. 현재 서울 거주만으로는 확인할 수 없어요.",
      "options": [
        {
          "value": "COMPLETED",
          "label": "기간 내 이사·전입신고를 모두 마쳤어요"
        },
        {
          "value": "OUTSIDE",
          "label": "이사 또는 전입신고가 기간에 맞지 않아요"
        },
        {
          "value": "UNKNOWN",
          "label": "확인 중이에요"
        }
      ]
    },
    {
      "id": "contract",
      "label": "신청 당시 계약·세대주·주민등록 조건을 모두 갖췄나요?",
      "help": "본인이 세대주이자 임대차계약의 임차인이며, 계약한 집에 주민등록이 있어야 해요. 부모·배우자 등 동거인이 있어도 가능해요.",
      "options": [
        {
          "value": "ALL",
          "label": "세 조건을 모두 갖췄어요"
        },
        {
          "value": "MISSING",
          "label": "갖추지 못한 조건이 있어요"
        },
        {
          "value": "UNKNOWN",
          "label": "확인 중이에요"
        }
      ]
    },
    {
      "id": "homeOwnership",
      "label": "신청 당시 본인 명의 주택이나 입주권이 있었나요?",
      "help": "분양권·조합원 입주권·공유지분도 포함해요. 전세사기 피해 주택을 경·공매로 취득한 경우 등은 예외를 확인해주세요. 청약의 무주택 기준과 달라요.",
      "options": [
        {
          "value": "NO_HOME",
          "label": "모두 없었어요"
        },
        {
          "value": "OWNS_NO_EXCEPTION",
          "label": "있었고 예외에도 해당하지 않아요"
        },
        {
          "value": "EXCEPTION_PENDING",
          "label": "전세사기 피해 등 예외 확인이 필요해요"
        },
        {
          "value": "UNKNOWN",
          "label": "확인 중이에요"
        }
      ]
    },
    {
      "id": "housingCost",
      "label": "공고 기준 주택 거래금액이 2억 원 이하인가요?",
      "help": "거래금액은 보증금 + 월세 × 100이에요. 전·월세 거주 여부도 확인해주세요. 월 소득이나 주택 매매가를 입력하는 항목이 아니에요.",
      "options": [
        {
          "value": "WITHIN_LIMIT",
          "label": "전·월세이며 거래금액이 2억 원 이하예요"
        },
        {
          "value": "OUTSIDE",
          "label": "전·월세가 아니거나 2억 원을 넘어요"
        },
        {
          "value": "UNKNOWN",
          "label": "확인 중이에요"
        }
      ]
    },
    {
      "id": "income",
      "label": "2026년 3월 건강보험료가 공고 기준 이하인가요?",
      "help": "공고 2쪽의 가구원 수·가입 유형별 중위소득 150% 기준표와 비교해주세요. 장기요양보험료는 빼고, 피부양자는 주소가 달라도 부양자 고지금액을 사용해요. 월급으로 대신 비교하지 않아요.",
      "options": [
        {
          "value": "WITHIN_LIMIT",
          "label": "공고 기준표로 확인 · 기준 이하"
        },
        {
          "value": "ABOVE_LIMIT",
          "label": "공고 기준표로 확인 · 기준 초과"
        },
        {
          "value": "DEPENDENT_PENDING",
          "label": "부양자 보험료 확인 중"
        },
        {
          "value": "HOUSEHOLD_PENDING",
          "label": "가구원 수·가입 유형 확인 중"
        },
        {
          "value": "DOCUMENTS_PENDING",
          "label": "보험료 조회 불가 · 대체 증빙 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "seoulSupport",
      "label": "이 공고 신청 전에 서울시 이 사업의 지원을 받은 적 있나요?",
      "help": "서울시 청년 중개보수·이사비 지원은 생애 1회예요. 자치구·LH·SH 등 다른 기관의 지원은 다음 질문에서 답해주세요.",
      "options": [
        {
          "value": "NONE",
          "label": "받은 적 없어요"
        },
        {
          "value": "RECEIVED",
          "label": "서울시 이 사업에서 지원받았어요"
        },
        {
          "value": "UNKNOWN",
          "label": "사업명·수혜 이력 확인 중"
        }
      ]
    },
    {
      "id": "otherSupport",
      "label": "다른 기관에서 어떤 비용을 지원받았나요?",
      "help": "신청 전까지 2022.1.1. 이후 서울 전입·서울 내 이사로 받은 지원을 확인해주세요. 자치구·중앙부처·LH·SH 등을 포함해요. 생필품비 등 지원 항목이 불명확하면 기관에서 확인해주세요.",
      "options": [
        {
          "value": "NONE",
          "label": "받은 적 없어요"
        },
        {
          "value": "BROKERAGE_ONLY",
          "label": "중개보수만 받았어요"
        },
        {
          "value": "MOVING_ONLY",
          "label": "이사비만 받았어요"
        },
        {
          "value": "BOTH",
          "label": "중개보수·이사비 모두 받았어요"
        },
        {
          "value": "UNKNOWN",
          "label": "지원 기관·항목 확인 중"
        }
      ]
    },
    {
      "id": "requestedCost",
      "label": "지원받으려는 비용은 무엇인가요?",
      "help": "중개보수·이사비 중 한 가지만 신청해도 돼요. 다른 기관에서 지원받은 비용은 제외하고 선택해주세요.",
      "options": [
        {
          "value": "BROKERAGE",
          "label": "중개보수만"
        },
        {
          "value": "MOVING",
          "label": "이사비만"
        },
        {
          "value": "BOTH",
          "label": "중개보수·이사비 모두"
        },
        {
          "value": "UNKNOWN",
          "label": "아직 정하지 않았어요"
        }
      ]
    },
    {
      "id": "parentRental",
      "label": "신청 당시 임차한 집이 부모님 소유였나요?",
      "help": "부모 소유 주택을 임차하면 참여 대상에서 제외돼요. 부모와 함께 살았는지가 아니라 임차주택의 소유자를 확인해주세요.",
      "options": [
        {
          "value": "CLEAR",
          "label": "부모 소유 주택이 아니었어요"
        },
        {
          "value": "RESTRICTED",
          "label": "부모 소유 주택이었어요"
        },
        {
          "value": "UNKNOWN",
          "label": "소유 관계 확인 중"
        }
      ]
    },
    {
      "id": "benefitReceipt",
      "label": "신청 당시 생계·의료·주거급여를 받고 있었나요?",
      "help": "세 급여 중 하나라도 받고 있었다면 참여 대상에서 제외돼요. 교육급여만 받는 경우는 이 수급 제한에 포함하지 않아요.",
      "options": [
        {
          "value": "CLEAR",
          "label": "세 급여 모두 받지 않았어요"
        },
        {
          "value": "RESTRICTED",
          "label": "한 가지 이상 받고 있었어요"
        },
        {
          "value": "UNKNOWN",
          "label": "수급 종류·시점 확인 중"
        }
      ]
    },
    {
      "id": "excludedResidency",
      "label": "신청 당시 외국인·재외국민에 해당했나요?",
      "help": "공고는 외국인·재외국민을 지원 대상에서 제외해요. 해당 여부가 불분명하면 담당 기관에서 확인해주세요. 국적명이나 증빙서류는 입력하지 않아요.",
      "options": [
        {
          "value": "CLEAR",
          "label": "둘 다 해당하지 않았어요"
        },
        {
          "value": "RESTRICTED",
          "label": "외국인 또는 재외국민이었어요"
        },
        {
          "value": "UNKNOWN",
          "label": "해당 여부 확인 중"
        }
      ]
    }
  ],
  "contentHash": "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "입력한 조건의 비교 결과이며, 증빙·기타 참여 제한·선발 심사는 별도예요.",
  "remainingChecks": [
    "보험료·가구원 수·가입 유형은 입력한 답변으로 비교했어요. 증빙 인정 여부와 소득 심사는 담당 기관에서 확인해주세요.",
    "어느 기관에서 어떤 비용을 지원받았는지 증빙을 확인해주세요. 중복지원 제한에 해당하지 않아도 비용과 지급액은 심사로 결정돼요.",
    "2024.1.1.~2026.4.14. 지출을 마친 비용과 증빙을 확인해주세요. 확인할 비용을 선택하면 중개보수·이사비의 인정 범위와 제외 항목을 안내해요.",
    "제출할 증빙서류가 인정되는지, 그 밖의 참여 제한이 있는지는 담당 기관에서 확인해주세요.",
    "생애 1회·최대 40만 원 실비 지원이며 우선선발·소득 순 심사를 거쳐요. 실제 선정과 지급은 공식 결과를 확인해주세요."
  ],
  "checks": [
    {
      "label": "공고의 출생일 기준",
      "evidence": "2026년 상반기 공고는 1986.1.1.~2007.12.31. 출생자를 대상으로 해요(양 끝 날짜 포함).",
      "questionId": "birthRange",
      "cases": [
        {
          "when": {
            "birthRange": [
              "IN_RANGE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "birthRange": [
              "OUTSIDE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "이사·전입신고 기간",
      "evidence": "2024.1.1. 이후 서울 전입 또는 서울 내 이사와 2026.4.14. 마감까지 전입신고가 필요해요.",
      "questionId": "move",
      "cases": [
        {
          "when": {
            "move": [
              "COMPLETED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "move": [
              "OUTSIDE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "계약·세대주·주민등록",
      "evidence": "신청자 본인이 세대주·임차인이며 임차주택에 주민등록이 있어야 해요. 동거인은 허용돼요.",
      "questionId": "contract",
      "cases": [
        {
          "when": {
            "contract": [
              "ALL"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "contract": [
              "MISSING"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "본인 주택 소유",
      "evidence": "본인 무주택이 원칙이며 분양권·입주권·공유지분도 포함해요. 전세사기 피해 주택 취득 등 예외는 기관 확인이 필요해요.",
      "questionId": "homeOwnership",
      "cases": [
        {
          "when": {
            "homeOwnership": [
              "NO_HOME"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "homeOwnership": [
              "OWNS_NO_EXCEPTION"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "homeOwnership": [
              "EXCEPTION_PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "피해 주택 취득에 따른 예외를 담당 기관에서 확인해주세요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "주택 거래금액",
      "evidence": "전·월세 주택의 보증금 + 월세 × 100이 2억 원 이하여야 해요.",
      "questionId": "housingCost",
      "cases": [
        {
          "when": {
            "housingCost": [
              "WITHIN_LIMIT"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "housingCost": [
              "OUTSIDE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "건강보험료 기준 소득",
      "evidence": "2026년 3월 건강보험료 고지금액(장기요양보험료 제외)이 공고 2쪽의 가구원 수·가입 유형별 중위소득 150% 기준 이하여야 해요. 피부양자는 주소가 분리돼도 부양자의 고지금액으로 비교해요. 조회가 어려우면 기관에서 대체 소득 증빙을 확인해야 해요.",
      "questionId": "income",
      "cases": [
        {
          "when": {
            "income": [
              "WITHIN_LIMIT"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "income": [
              "ABOVE_LIMIT"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "income": [
              "DEPENDENT_PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "피부양자는 본인 보험료가 0원이어도 충족으로 판단하지 않아요. 부양자의 2026년 3월 고지금액을 확인해주세요."
        },
        {
          "when": {
            "income": [
              "HOUSEHOLD_PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "공고 기준 가구원 수와 가입 유형을 확인한 뒤 해당 보험료 기준과 비교해주세요."
        },
        {
          "when": {
            "income": [
              "DOCUMENTS_PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "보험료를 조회할 수 없다는 이유로 불충족 처리하지 않아요. 담당 기관에서 대체 소득 증빙을 확인해주세요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "중복지원 제한",
      "evidence": "서울시 이 사업은 생애 1회예요. 공고는 2022.1.1. 이후 서울 전입·서울 내 이사에 대한 서울시·타 기관 지원 이력을 확인해요. 타 기관에서 한 종류 비용만 받았다면 다른 비용에 한해 지원을 확인할 수 있어요. 근거: 공고 1·3쪽.",
      "questionId": "seoulSupport",
      "cases": [
        {
          "when": {
            "seoulSupport": [
              "",
              "NONE",
              "UNKNOWN"
            ],
            "otherSupport": [
              "BOTH"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "다른 기관에서 중개보수와 이사비를 모두 지원받아 중복지원 제한에 해당해요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "NONE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변에는 이전 지원 이력이 없어요. 신청 비용의 인정 여부는 별도 확인이 필요해요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "BROKERAGE_ONLY",
              "MOVING_ONLY"
            ],
            "requestedCost": [
              "",
              "UNKNOWN"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "이번에 지원받으려는 비용을 선택해주세요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "BROKERAGE_ONLY"
            ],
            "requestedCost": [
              "BROKERAGE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "이미 지원받은 중개보수를 다시 신청하는 경우예요. 이사비만 선택하면 별도로 확인할 수 있어요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "BROKERAGE_ONLY"
            ],
            "requestedCost": [
              "MOVING"
            ]
          },
          "outcome": "MET",
          "explanation": "타 기관에서 중개보수만 받았다면 이사비의 중복지원 조건은 충족해요. 실제 비용과 증빙은 별도 심사해요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "BROKERAGE_ONLY"
            ],
            "requestedCost": [
              "BOTH"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "이미 지원받은 중개보수는 중복돼요. 이사비만 선택해 다시 확인해주세요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "MOVING_ONLY"
            ],
            "requestedCost": [
              "BROKERAGE"
            ]
          },
          "outcome": "MET",
          "explanation": "타 기관에서 이사비만 받았다면 중개보수의 중복지원 조건은 충족해요. 실제 비용과 증빙은 별도 심사해요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "MOVING_ONLY"
            ],
            "requestedCost": [
              "MOVING"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "이미 지원받은 이사비를 다시 신청하는 경우예요. 중개보수만 선택하면 별도로 확인할 수 있어요."
        },
        {
          "when": {
            "seoulSupport": [
              "NONE"
            ],
            "otherSupport": [
              "MOVING_ONLY"
            ],
            "requestedCost": [
              "BOTH"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "이미 지원받은 이사비는 중복돼요. 중개보수만 선택해 다시 확인해주세요."
        },
        {
          "when": {
            "seoulSupport": [
              "RECEIVED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "서울시 이 사업에서 이미 지원받았다면 다른 비용도 다시 지원받을 수 없어요. 생애 1회 지원이에요."
        }
      ],
      "unknownExplanation": "서울시 사업에서 지원받았는지, 다른 기관에서는 어떤 비용을 지원받았는지 확인해주세요.",
      "providedAnswers": [
        {
          "questionId": "seoulSupport",
          "prefix": "서울시 사업: "
        },
        {
          "questionId": "otherSupport",
          "prefix": "타 기관: "
        },
        {
          "questionId": "requestedCost",
          "prefix": "신청할 비용: "
        }
      ],
      "separator": " / "
    },
    {
      "label": "부모 소유 주택 임차 제한",
      "evidence": "공고 3쪽은 부모 소유 주택을 임차하는 경우를 참여 제한 대상으로 정해요. 부모와의 동거 여부와 구분해요.",
      "questionId": "parentRental",
      "cases": [
        {
          "when": {
            "parentRental": [
              "CLEAR"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "parentRental": [
              "RESTRICTED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "생계·의료·주거급여 수급 제한",
      "evidence": "공고 3쪽은 생계·의료·주거급여 수급자를 참여 제한 대상으로 정해요. 교육급여만 받는 경우는 이 세 급여 수급에 포함하지 않아요.",
      "questionId": "benefitReceipt",
      "cases": [
        {
          "when": {
            "benefitReceipt": [
              "CLEAR"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "benefitReceipt": [
              "RESTRICTED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "외국인·재외국민 제한",
      "evidence": "공고 2쪽은 외국인·재외국민을 지원 대상에서 제외해요. 신청 당시 해당 여부를 확인해요.",
      "questionId": "excludedResidency",
      "cases": [
        {
          "when": {
            "excludedResidency": [
              "CLEAR"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "excludedResidency": [
              "RESTRICTED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    }
  ],
  "periodNotice": {
    "opensAt": "2026-04-01T01:00:00Z",
    "closesAt": "2026-04-14T09:00:00Z",
    "before": "접수 전이에요. 접수 기간은 2026.4.1. 10:00~4.14. 18:00(서울)이에요.",
    "open": "접수 기간은 2026.4.1. 10:00~4.14. 18:00(서울)이에요.",
    "closed": "2026년 상반기 접수는 4월 14일 18:00(서울)에 마감됐어요."
  },
  "birthBinding": {
    "questionId": "birthRange",
    "minimumInclusive": "1986-01-01",
    "maximumInclusive": "2007-12-31",
    "below": "OUTSIDE",
    "within": "IN_RANGE",
    "above": "OUTSIDE"
  },
  "remainingVariant": {
    "index": 2,
    "questionId": "requestedCost",
    "byValue": {
      "BROKERAGE": "2024.1.1.~2026.4.14. 지출을 마친 비용과 증빙을 확인해주세요. 중개보수는 임대차계약 체결 비용을 확인하며, 재계약·중도 퇴실로 발생한 비용은 제외돼요.",
      "MOVING": "2024.1.1.~2026.4.14. 지출을 마친 비용과 증빙을 확인해주세요. 이사비는 개인용달·(반)포장이사·사다리차 이용비 등을 확인해요. 청소·택배·대중교통·택시·렌터카 비용은 제외돼요.",
      "BOTH": "2024.1.1.~2026.4.14. 지출을 마친 비용과 증빙을 확인해주세요. 중개보수는 임대차계약 체결 비용을 확인하며, 재계약·중도 퇴실로 발생한 비용은 제외돼요. 이사비는 개인용달·(반)포장이사·사다리차 이용비 등을 확인해요. 청소·택배·대중교통·택시·렌터카 비용은 제외돼요."
    }
  }
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260614005400213232', 'ba390000-0000-4000-8024-000000000004');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000005', '20260430005400113009', 'youth-tomorrow-savings-2026-v1', $rule$
{
  "policyNumber": "20260430005400113009",
  "ruleVersion": "youth-tomorrow-savings-2026-v1",
  "scope": "2026년 5월 청년내일저축계좌 신규 가입 조건",
  "reason": "연령·근로소득·가구소득·중복참여를 확인해요. 수집 안내와 다른 기준은 2026년 사업 지침을 적용해요.",
  "sourceUrl": "https://hope.welfareinfo.or.kr/pds/GuideLine.pdf",
  "questions": [
    {
      "id": "birthRange",
      "label": "2026년 5월 모집의 출생일 범위에 해당하나요?",
      "help": "1986.5.1.~2011.5.31. 출생자가 대상이에요(양 끝 날짜 포함). 사업 지침은 신청 월에 만 15세 또는 만 40세가 되는 사람까지 포함해요.",
      "options": [
        {
          "value": "IN_RANGE",
          "label": "1986.5.1.~2011.5.31. 출생"
        },
        {
          "value": "OUTSIDE_RANGE",
          "label": "이 출생일 범위에 해당하지 않아요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "workType",
      "label": "모집 신청 당시 어떤 소득활동을 했나요?",
      "help": "자활기업·자활근로사업단 소득은 인정해요. 공공 일자리는 인건비 지원 방식·별도 채용 등 예외를 주민센터에서 확인해주세요.",
      "options": [
        {
          "value": "EMPLOYMENT_OR_BUSINESS",
          "label": "일반 근로·사업"
        },
        {
          "value": "SELF_RELIANCE",
          "label": "자활기업·자활근로"
        },
        {
          "value": "PUBLIC_WORK_CONFIRMED",
          "label": "공공 일자리 · 인정 확인"
        },
        {
          "value": "PUBLIC_WORK_PENDING",
          "label": "공공 일자리 · 확인 중"
        },
        {
          "value": "EXCLUDED_ONLY",
          "label": "근로장학금·실업·육아휴직급여만"
        },
        {
          "value": "UNPAID_ONLY",
          "label": "무급근로만"
        },
        {
          "value": "NO_WORK",
          "label": "근로·사업활동 없음"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "monthlyIncome",
      "label": "신청 당시 인정되는 본인 월 근로·사업소득은 얼마인가요?",
      "help": "본인의 세전 근로·사업소득 기준이에요. 근로장학금·실업급여·육아휴직급여는 더하지 마세요. 증빙 인정 여부를 확인 중이면 ‘소득 증빙·금액 확인 중’을 선택해주세요.",
      "options": [
        {
          "value": "AT_LEAST_100K",
          "label": "월 10만 원 이상"
        },
        {
          "value": "BELOW_100K",
          "label": "월 10만 원 미만"
        },
        {
          "value": "DOCUMENTS_PENDING",
          "label": "소득 증빙·금액 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "householdIncome",
      "label": "신청 당시 가구 소득인정액 기준을 확인했나요?",
      "help": "2026년 가입 기준은 중위소득 50% 이하예요. 소득인정액에는 소득과 재산 환산액이 반영돼요. 가구원 범위와 소득인정액은 주민센터에서 확인해주세요. 월급·건강보험료로 대신 비교할 수 없어요.",
      "options": [
        {
          "value": "UP_TO_50_CONFIRMED",
          "label": "확인 완료 · 50% 이하"
        },
        {
          "value": "OVER_50_CONFIRMED",
          "label": "확인 완료 · 50% 초과"
        },
        {
          "value": "ASSESSMENT_PENDING",
          "label": "가구 범위·소득인정액 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "duplicateParticipation",
      "label": "다른 자산형성사업의 참여 이력을 확인했나요?",
      "help": "본인·가구원의 현재·과거 참여와 앞으로 참여할 사업을 확인해요. 가구원의 가입이나 지원금 환수 이력만으로 가입이 제한되지는 않으니 사업별로 주민센터에서 확인해주세요.",
      "options": [
        {
          "value": "NO_HISTORY",
          "label": "참여·수혜·예정 없음"
        },
        {
          "value": "ALLOWED_CONFIRMED",
          "label": "참여 이력 · 가입 가능 확인"
        },
        {
          "value": "RESTRICTED_CONFIRMED",
          "label": "중복참여 제한 확인"
        },
        {
          "value": "HISTORY_PENDING",
          "label": "참여·수혜·환수 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "f3709a60376cdaf861ee232c1fc411292f2d3bdcb28c807ca87190a19698733c",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "신규 가입 조건만 비교했어요.",
  "remainingChecks": [
    "수집 안내에 소득·연령 기준 차이가 있어요. 이 결과는 2026년 신규 모집 공고와 사업 지침의 일부 조건만 비교했어요.",
    "신청서·소득 증빙·가구 조사, 제외업종과 신용정보·계좌 개설 가능 여부는 주민센터에서 확인해주세요.",
    "조건이 맞아도 선정 심사가 남아 있어요. 근로 유지·저축·교육 이수·자금사용계획서 등 가입 후 지급 조건도 별도로 확인해주세요."
  ],
  "checks": [
    {
      "label": "모집 기준 출생일",
      "evidence": "2026년 5월 모집은 1986.5.1.~2011.5.31. 출생자가 대상이에요. 신청 월에 만 15세 또는 만 40세가 되는 사람을 포함해요.",
      "questionId": "birthRange",
      "cases": [
        {
          "when": {
            "birthRange": [
              "IN_RANGE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "birthRange": [
              "OUTSIDE_RANGE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "현재 만 나이가 아닌 2026년 5월 모집의 출생일 범위를 확인해주세요."
    },
    {
      "label": "본인 근로·사업소득",
      "evidence": "신청 당시 인정되는 근로활동과 본인 세전 근로·사업소득 월 10만 원 이상이 필요해요. 자활근로는 인정하지만 근로장학금·실업급여·육아휴직급여만으로는 가입할 수 없어요.",
      "questionId": "workType",
      "cases": [
        {
          "when": {
            "workType": [
              "EMPLOYMENT_OR_BUSINESS",
              "PUBLIC_WORK_CONFIRMED",
              "SELF_RELIANCE"
            ],
            "monthlyIncome": [
              "AT_LEAST_100K"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "workType": [
              "EMPLOYMENT_OR_BUSINESS",
              "EXCLUDED_ONLY",
              "NO_WORK",
              "PUBLIC_WORK_CONFIRMED",
              "SELF_RELIANCE",
              "UNPAID_ONLY"
            ],
            "monthlyIncome": [
              "BELOW_100K"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "workType": [
              "EXCLUDED_ONLY",
              "NO_WORK",
              "UNPAID_ONLY"
            ],
            "monthlyIncome": [
              "",
              "AT_LEAST_100K",
              "DOCUMENTS_PENDING",
              "UNKNOWN"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "workType": [
              "PUBLIC_WORK_PENDING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "인건비 지원 방식·별도 채용 등에 따른 소득 인정 여부를 주민센터에서 확인해주세요."
        }
      ],
      "unknownExplanation": "신청 당시 근로활동과 인정되는 월 소득을 증빙으로 확인해주세요. 확인 중인 소득을 0원으로 처리하지 않아요.",
      "providedAnswers": [
        {
          "questionId": "workType",
          "prefix": ""
        },
        {
          "questionId": "monthlyIncome",
          "prefix": ""
        }
      ],
      "separator": " · "
    },
    {
      "label": "가구 소득인정액",
      "evidence": "신규 가입은 신청 당시 가구 소득인정액이 2026년 기준 중위소득 50% 이하여야 해요. 가입 후 소득 유지 기준과 달라요.",
      "questionId": "householdIncome",
      "cases": [
        {
          "when": {
            "householdIncome": [
              "UP_TO_50_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "householdIncome": [
              "OVER_50_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "주민센터에서 가구 범위와 소득·재산을 반영한 소득인정액을 확인해주세요."
    },
    {
      "label": "중복참여 제한",
      "evidence": "유사 자산형성사업은 사업 종류·가입자·지원금 수령 또는 환수 여부에 따라 중복참여 기준이 달라요.",
      "questionId": "duplicateParticipation",
      "cases": [
        {
          "when": {
            "duplicateParticipation": [
              "ALLOWED_CONFIRMED",
              "NO_HISTORY"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "duplicateParticipation": [
              "RESTRICTED_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "본인과 가구원이 참여한 사업명, 지원금 수령·환수 이력으로 중복참여 제한을 확인해주세요."
    }
  ],
  "periodNotice": {
    "opensAt": "2026-05-03T15:00:00Z",
    "closesAt": "2026-05-20T15:00:00Z",
    "before": "접수 전이에요. 이 모집의 접수 기간은 2026년 5월 4~20일이에요.",
    "open": "이 모집의 접수 기간은 2026년 5월 4~20일이에요.",
    "closed": "2026년 5월 4~20일 모집은 접수가 마감됐어요. 다음 모집 기준은 별도로 확인해주세요."
  },
  "birthBinding": {
    "questionId": "birthRange",
    "minimumInclusive": "1986-05-01",
    "maximumInclusive": "2011-05-31",
    "below": "OUTSIDE_RANGE",
    "within": "IN_RANGE",
    "above": "OUTSIDE_RANGE"
  },
  "ageNotice": "수집 안내와 소득·출생일 기준이 달라 2026년 사업 지침을 적용했어요."
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260430005400113009', 'ba390000-0000-4000-8024-000000000005');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000006', '20260527005400113223', 'guarantee-fee-2026-v1', $rule$
{
  "policyNumber": "20260527005400113223",
  "ruleVersion": "guarantee-fee-2026-v1",
  "scope": "2026년 서울 보증료 지원 공통 조건",
  "reason": "보증 가입·보증금·무주택·소득을 확인해요. 청년 외 연령도 대상이며, 접수와 예산은 주소지 신청처에서 확인해주세요.",
  "sourceUrl": "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/161300000103",
  "questions": [
    {
      "id": "guarantee",
      "label": "신청일에 유효한 반환보증에 가입하고 보증료를 냈나요?",
      "help": "HUG·HF·SGI의 전세보증금반환보증 기준이에요. 전세대출 보증이나 임대인의 임대보증금보증과 구분해주세요.",
      "options": [
        {
          "value": "VALID_PAID",
          "label": "유효한 반환보증 · 납부 완료"
        },
        {
          "value": "NOT_JOINED",
          "label": "미가입 또는 다른 보증만 가입"
        },
        {
          "value": "EXPIRED",
          "label": "반환보증 만료·해지"
        },
        {
          "value": "UNPAID",
          "label": "보증료 미납"
        },
        {
          "value": "PENDING",
          "label": "가입·갱신·납부 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "deposit",
      "label": "임대차계약서의 보증금은 얼마인가요?",
      "help": "전세대출 잔액이나 낸 보증료가 아닌 임차보증금이에요.",
      "options": [
        {
          "value": "UP_TO_300M",
          "label": "3억 원 이하"
        },
        {
          "value": "OVER_300M",
          "label": "3억 원 초과"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "homeOwnership",
      "label": "본인과 배우자 모두 무주택자인가요?",
      "help": "신청일 기준으로 확인해요. 분양권·입주권도 포함하며, 미혼이면 본인만 확인해주세요.",
      "options": [
        {
          "value": "NO_HOME",
          "label": "모두 무주택"
        },
        {
          "value": "OWNS_HOME",
          "label": "주택·분양권·입주권 소유"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "applicantType",
      "label": "신청일에 어떤 지원 유형에 해당하나요?",
      "help": "혼인신고 7년 이내면 나이와 관계없이 신혼부부를 선택해요. 그 외 서울 청년은 만 19~39세예요. 유형이 불분명하면 신청처에서 확인해주세요.",
      "options": [
        {
          "value": "NEWLYWED",
          "label": "신혼부부 · 혼인신고 7년 이내"
        },
        {
          "value": "YOUTH",
          "label": "만 19~39세 · 신혼부부 아님"
        },
        {
          "value": "OTHER",
          "label": "청년·신혼부부에 해당 안 됨"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "incomeBasis",
      "label": "신청용 소득서류의 기준을 확인했나요?",
      "help": "신청처 기준의 소득 합산 범위·대상 연도·증빙을 확인해요. 기혼자는 배우자 서류도 필요해요. 월급이나 매출로 대신하지 마세요.",
      "options": [
        {
          "value": "CONFIRMED",
          "label": "소득 합산 범위·서류 확인"
        },
        {
          "value": "PENDING",
          "label": "소득서류·적용 기준 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "annualIncome",
      "label": "신청용 서류로 확인한 연소득은 얼마인가요?",
      "help": "신혼부부는 부부 합산 연소득이에요. 소득이 없다면 신고사실없음 등 신청처가 인정하는 증빙도 확인해주세요.",
      "options": [
        {
          "value": "UP_TO_50M",
          "label": "5,000만 원 이하"
        },
        {
          "value": "OVER_50_TO_60M",
          "label": "5천만 초과~6천만 원 이하"
        },
        {
          "value": "OVER_60_TO_75M",
          "label": "6천만 초과~7천5백만 원 이하"
        },
        {
          "value": "OVER_75M",
          "label": "7,500만 원 초과"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "4dc5e18b6a09b35f00cf00c7be3a608689f6c2d33ee8b191c38a9823dac8cc24",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "보증 가입·보증금·무주택·소득의 확인 결과예요. 신청 자격과 지급액은 주소지 신청처에서 최종 확인해주세요.",
  "remainingChecks": [
    "신청일 주소·거주와 계약서·보증서의 명의, 국적·재외국민 기준을 주소지 신청처에서 확인해주세요.",
    "등록임대사업자의 임대주택, 법인 임차인과 이미 지원받은 동일 보증서의 재신청은 지원 제외 대상이에요.",
    "주택 소유·지원 유형·소득서류와 그 밖의 지원 제외 사유는 신청처 심사가 필요해요.",
    "예산 소진 시 접수가 끝날 수 있어요. 보증 가입일·유형·납부액에 따른 실제 지원액과 지급 여부는 신청처에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "반환보증 가입·납부",
      "evidence": "신청일에 유효한 HUG·HF·SGI 전세보증금반환보증에 가입하고 보증료를 납부해야 해요.",
      "questionId": "guarantee",
      "cases": [
        {
          "when": {
            "guarantee": [
              "VALID_PAID"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "guarantee": [
              "EXPIRED",
              "NOT_JOINED",
              "UNPAID"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "보증 종류·유효기간과 납부 여부를 확인해주세요. 처리 중인 가입·갱신을 완료로 보지 않아요."
    },
    {
      "label": "임차보증금",
      "evidence": "임차보증금은 3억 원 이하여야 해요.",
      "questionId": "deposit",
      "cases": [
        {
          "when": {
            "deposit": [
              "UP_TO_300M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "deposit": [
              "OVER_300M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "임대차계약서의 보증금을 확인해주세요."
    },
    {
      "label": "본인·배우자 무주택",
      "evidence": "신청인과 배우자 모두 무주택이어야 하며 분양권·입주권도 소유 여부에 포함해요.",
      "questionId": "homeOwnership",
      "cases": [
        {
          "when": {
            "homeOwnership": [
              "NO_HOME"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "homeOwnership": [
              "OWNS_HOME"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "본인과 배우자의 주택·분양권·입주권 소유 여부를 확인해주세요."
    },
    {
      "label": "유형별 연소득",
      "evidence": "청년은 연 5,000만 원, 청년 외는 6,000만 원, 신혼부부는 부부 합산 7,500만 원 이하여야 해요.",
      "questionId": "applicantType",
      "cases": [
        {
          "when": {
            "applicantType": [
              "NEWLYWED"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_50_TO_60M",
              "OVER_60_TO_75M",
              "UP_TO_50M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "applicantType": [
              "OTHER",
              "YOUTH"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "UP_TO_50M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "applicantType": [
              "OTHER"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_50_TO_60M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "applicantType": [
              "NEWLYWED",
              "OTHER",
              "YOUTH"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_75M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "applicantType": [
              "YOUTH"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_50_TO_60M",
              "OVER_60_TO_75M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "applicantType": [
              "OTHER"
            ],
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_60_TO_75M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "지원 유형과 신청용 소득의 합산 범위·대상 연도·증빙을 확인해주세요. 기준이 미확인이면 금액만으로 비교하지 않아요.",
      "providedAnswers": [
        {
          "questionId": "applicantType",
          "prefix": ""
        },
        {
          "questionId": "incomeBasis",
          "prefix": ""
        },
        {
          "questionId": "annualIncome",
          "prefix": ""
        }
      ],
      "separator": " / "
    }
  ]
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260527005400113223', 'ba390000-0000-4000-8024-000000000006');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000007', '20260724005400113307', 'haetsalron-youth-2026-v1', $rule$
{
  "policyNumber": "20260724005400113307",
  "ruleVersion": "haetsalron-youth-2026-v1",
  "scope": "2026년 햇살론유스 보증 공통 조건",
  "reason": "연령·이용 대상·소득·남은 생애 보증한도를 확인해요. 조건이 맞아도 서민금융진흥원의 보증심사와 은행의 대출심사가 필요해요.",
  "sourceUrl": "https://www.kinfa.or.kr/financialProduct/hessalLoanYoos.do",
  "questions": [
    {
      "id": "age",
      "label": "보증신청일 기준 만 나이는 어떻게 되나요?",
      "help": "만 19~34세가 대상이에요. 군입대 예정자의 거치기간 연장은 신청 연령 연장과 달라요.",
      "options": [
        {
          "value": "AGE_19_TO_34",
          "label": "만 19~34세"
        },
        {
          "value": "UNDER_19",
          "label": "만 19세 미만"
        },
        {
          "value": "OVER_34",
          "label": "만 35세 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "applicantType",
      "label": "서민금융진흥원 기준으로 어떤 이용 대상에 해당하나요?",
      "help": "대학(원)생·학점은행제 수강자·미취업청년은 취업준비생 유형이에요. 학적·근로·사업이 겹치거나 기간이 불명확하면 적용 유형을 확인해주세요.",
      "options": [
        {
          "value": "PREPARING_CONFIRMED",
          "label": "취업준비생 · 학적·근로 기준 확인"
        },
        {
          "value": "EARLY_EMPLOYEE",
          "label": "사회초년생 · 중소기업 1년 이하"
        },
        {
          "value": "YOUNG_BUSINESS",
          "label": "청년사업자 · 창업 1년 이하"
        },
        {
          "value": "NOT_TARGET_CONFIRMED",
          "label": "서민금융진흥원 확인 · 대상 아님"
        },
        {
          "value": "PENDING",
          "label": "학적·근로·사업 기준 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "incomeBasis",
      "label": "보증신청용 소득 기준을 확인했나요?",
      "help": "서민금융진흥원에서 요구하는 증빙과 산정 기간을 확인해요. 무소득·단기 근로·사업소득의 인정 여부가 확인 중이면 그대로 선택해주세요.",
      "options": [
        {
          "value": "CONFIRMED",
          "label": "증빙·소득 산정 기준 확인"
        },
        {
          "value": "PENDING",
          "label": "증빙·적용 기준 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "annualIncome",
      "label": "보증신청 기준으로 확인한 본인 연소득은 얼마인가요?",
      "help": "본인의 연소득을 확인해요. 월급·사업 매출·가구소득을 그대로 넣거나 임의로 연환산하지 마세요.",
      "options": [
        {
          "value": "UP_TO_35M",
          "label": "3,500만 원 이하"
        },
        {
          "value": "OVER_35M",
          "label": "3,500만 원 초과"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "lifetimeLimit",
      "label": "서민금융진흥원에서 확인한 생애 보증한도가 남아 있나요?",
      "help": "생애 한도는 1,200만 원이며 갚아도 복원되지 않아요. 현재 대출 잔액이나 이번에 빌릴 수 있는 금액과 구분해주세요.",
      "options": [
        {
          "value": "REMAINING_CONFIRMED",
          "label": "남은 생애 한도 있음"
        },
        {
          "value": "EXHAUSTED_CONFIRMED",
          "label": "생애 한도 전액 사용"
        },
        {
          "value": "PENDING",
          "label": "이용 이력·남은 한도 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "1fbde72fe6ad25caf843889a0571a62c817ddd8b246df04093bd65710eb22df5",
  "validFrom": "2025-12-31T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "연령·이용 대상·소득·생애 보증한도의 확인 결과예요. 실제 보증·대출 승인과 이용 조건은 별도 심사가 필요해요.",
  "remainingChecks": [
    "신분·학적·재직·사업기간·소득 증빙과 재산 보유 등 보증 제외 사유는 서민금융진흥원에서 확인해야 해요.",
    "학점은행제 수강·학점 인정, 단기 근로·겸업 등의 적용 유형은 서민금융진흥원에서 확인해주세요.",
    "남은 생애 한도가 있어도 기간별·용도별 한도, 재신청 간격과 자금 용도 증빙을 별도로 확인해야 해요.",
    "금융교육과 보증심사·은행 대출심사가 필요해요. 실제 승인 여부·대출액·금리·보증료·상환 조건은 심사 과정에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "보증신청일 연령",
      "evidence": "보증신청일 기준 만 19~34세가 대상이에요. 군입대 예정자의 추가 거치기간은 연령 상한 연장이 아니에요.",
      "questionId": "age",
      "cases": [
        {
          "when": {
            "age": [
              "AGE_19_TO_34"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "OVER_34",
              "UNDER_19"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "실제 보증신청일의 만 나이를 확인해주세요."
    },
    {
      "label": "이용 대상",
      "evidence": "취업준비생, 중소기업에 1년 이하 재직한 사회초년생, 창업 1년 이하인 청년 개인사업자가 대상이에요.",
      "questionId": "applicantType",
      "cases": [
        {
          "when": {
            "applicantType": [
              "EARLY_EMPLOYEE",
              "PREPARING_CONFIRMED",
              "YOUNG_BUSINESS"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "applicantType": [
              "NOT_TARGET_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "학적·재직·사업 이력과 적용 유형을 서민금융진흥원 기준으로 확인해주세요. 현재 취업상태만으로 판단하지 않아요."
    },
    {
      "label": "본인 연소득",
      "evidence": "서민금융진흥원 기준으로 확인한 본인 연소득이 3,500만 원 이하여야 해요.",
      "questionId": "incomeBasis",
      "cases": [
        {
          "when": {
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "UP_TO_35M"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "incomeBasis": [
              "CONFIRMED"
            ],
            "annualIncome": [
              "OVER_35M"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "소득 증빙·산정 기간과 금액을 확인해주세요. 기준이 미확인이면 금액만으로 비교하지 않아요.",
      "providedAnswers": [
        {
          "questionId": "incomeBasis",
          "prefix": ""
        },
        {
          "questionId": "annualIncome",
          "prefix": ""
        }
      ],
      "separator": " / "
    },
    {
      "label": "남은 생애 보증한도",
      "evidence": "동일인 생애 보증한도는 1,200만 원이에요. 이미 이용한 금액은 상환해도 한도가 복원되지 않아요.",
      "questionId": "lifetimeLimit",
      "cases": [
        {
          "when": {
            "lifetimeLimit": [
              "REMAINING_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "lifetimeLimit": [
              "EXHAUSTED_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "서민금융진흥원에서 이용 이력과 남은 생애 보증한도를 확인해주세요. 대출 잔액으로 계산하지 않아요."
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 19,
    "maximumInclusive": 34,
    "referenceDate": null,
    "below": "UNDER_19",
    "within": "AGE_19_TO_34",
    "above": "OVER_34",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 보증 신청 시 연령이에요. 신청일이 달라지면 다시 확인해주세요."
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260724005400113307', 'ba390000-0000-4000-8024-000000000007');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000008', '20260421005400112773', 'miso-youth-future-2026-v1', $rule$
{
  "policyNumber": "20260421005400112773",
  "ruleVersion": "miso-youth-future-2026-v1",
  "scope": "2026년 청년 미래이음 대출 기본 조건",
  "reason": "연령·취업·창업 상태와 신용평점·수급 자격·근로장려금 요건을 확인해요. 최종 대출 여부는 지점 심사로 결정돼요.",
  "sourceUrl": "https://www.kinfa.or.kr/financialProduct/youngFutureLinkLoan.do",
  "questions": [
    {
      "id": "age",
      "label": "대출신청일 기준 만 나이는 어떻게 되나요?",
      "help": "만 19~34세가 대상이에요. 실제 신청일의 만 나이를 확인해주세요.",
      "options": [
        {
          "value": "AGE_19_TO_34",
          "label": "만 19~34세"
        },
        {
          "value": "UNDER_19",
          "label": "만 19세 미만"
        },
        {
          "value": "OVER_34",
          "label": "만 35세 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "employment",
      "label": "미소금융 지점에서 확인한 취업·창업 상태는 무엇인가요?",
      "help": "미취업 또는 취업·창업 후 1년 이내가 대상이에요. 근로·사업을 병행하거나 인정 기간을 모르면 ‘확인 중’을 선택해주세요.",
      "options": [
        {
          "value": "UNEMPLOYED",
          "label": "미취업 · 지점 확인 완료"
        },
        {
          "value": "EARLY_EMPLOYEE",
          "label": "취업 1년 이내 · 지점 확인 완료"
        },
        {
          "value": "EARLY_BUSINESS",
          "label": "창업 1년 이내 · 지점 확인 완료"
        },
        {
          "value": "NOT_TARGET",
          "label": "대상 아님 · 지점 확인 완료"
        },
        {
          "value": "PENDING",
          "label": "취·창업 이력 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "credit",
      "label": "개인신용평점 하위 20%에 해당하나요?",
      "help": "지점의 신용평가 기준으로 확인해주세요. 아래 신용평점·수급 자격·근로장려금 중 하나만 해당하면 돼요.",
      "options": [
        {
          "value": "YES",
          "label": "해당해요 · 확인 완료"
        },
        {
          "value": "NO",
          "label": "해당하지 않아요 · 확인 완료"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "welfare",
      "label": "기초생활수급자 또는 차상위계층 이하에 해당하나요?",
      "help": "신청 시점의 수급·차상위 자격을 증빙서류로 확인해주세요.",
      "options": [
        {
          "value": "YES",
          "label": "해당해요 · 확인 완료"
        },
        {
          "value": "NO",
          "label": "해당하지 않아요 · 확인 완료"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "earnedIncomeCredit",
      "label": "근로장려금 신청 자격 요건에 해당하나요?",
      "help": "적용 연도와 신청 자격·증빙서류를 미소금융 지점에서 확인해주세요. 과거 수령 이력만으로는 확인할 수 없어요.",
      "options": [
        {
          "value": "YES",
          "label": "해당해요 · 확인 완료"
        },
        {
          "value": "NO",
          "label": "해당하지 않아요 · 확인 완료"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "c2ba149dd238ced25aab441f1ff595079b0b2fb6b7d25f4d71a97a21ce3d3788",
  "validFrom": "2026-03-30T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "입력한 기본 조건의 비교 결과예요. 지원 제한과 예외, 최종 대출심사는 미소금융 지점에서 확인해야 해요.",
  "remainingChecks": [
    "자금 용도·상환 심사·재무상담·신청 서류는 미소금융 지점에서 확인해주세요.",
    "신용정보 등재, 재산의 법적 절차, 국적·해외체류 등 지원 제한과 예외를 확인해야 해요. 성실상환·면책 예외가 있어 신용정보 등재만으로 단정하지 않아요.",
    "취·창업 기간과 신용평점·수급·근로장려금 자격의 적용 기준 및 증빙을 확인해주세요.",
    "최종 대출 여부·금액·금리·기간은 대출심사에서 정해져요. 햇살론유스 이용 이력만으로 중복 이용 불가로 판단하지 않아요."
  ],
  "checks": [
    {
      "label": "대출신청일 연령",
      "evidence": "대출신청일 기준 만 19~34세가 대상이에요.",
      "questionId": "age",
      "cases": [
        {
          "when": {
            "age": [
              "AGE_19_TO_34"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "OVER_34",
              "UNDER_19"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "실제 대출신청일의 만 나이를 확인해주세요."
    },
    {
      "label": "취·창업 상태",
      "evidence": "미취업 또는 취·창업 1년 이내 청년이 대상이에요.",
      "questionId": "employment",
      "cases": [
        {
          "when": {
            "employment": [
              "EARLY_BUSINESS",
              "EARLY_EMPLOYEE",
              "UNEMPLOYED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "employment": [
              "NOT_TARGET"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "취·창업 이력과 기간의 적용 기준을 미소금융 지점에서 확인해주세요."
    },
    {
      "label": "신용평점·수급·근로장려금 요건",
      "evidence": "개인신용평점 하위 20%, 기초생활수급자·차상위계층 이하, 근로장려금 신청 자격 중 하나에 해당해야 해요.",
      "questionId": "credit",
      "cases": [
        {
          "when": {
            "credit": [
              "",
              "NO",
              "UNKNOWN"
            ],
            "earnedIncomeCredit": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "세 요건 중 하나 이상을 충족해요."
        },
        {
          "when": {
            "credit": [
              "",
              "NO",
              "UNKNOWN"
            ],
            "welfare": [
              "YES"
            ],
            "earnedIncomeCredit": [
              "",
              "NO",
              "UNKNOWN"
            ]
          },
          "outcome": "MET",
          "explanation": "세 요건 중 하나 이상을 충족해요."
        },
        {
          "when": {
            "credit": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "세 요건 중 하나 이상을 충족해요."
        },
        {
          "when": {
            "credit": [
              "NO"
            ],
            "welfare": [
              "NO"
            ],
            "earnedIncomeCredit": [
              "NO"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "세 요건 모두 비해당으로 확인됐어요."
        }
      ],
      "unknownExplanation": "아직 확인하지 못한 요건이 있어요. 세 요건 중 하나에 해당하는지 확인해주세요.",
      "providedAnswers": [
        {
          "questionId": "credit",
          "prefix": "신용평점: "
        },
        {
          "questionId": "welfare",
          "prefix": "수급·차상위: "
        },
        {
          "questionId": "earnedIncomeCredit",
          "prefix": "근로장려금: "
        }
      ],
      "separator": " / "
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 19,
    "maximumInclusive": 34,
    "referenceDate": null,
    "below": "UNDER_19",
    "within": "AGE_19_TO_34",
    "above": "OVER_34",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 대출 신청 시 연령이에요. 신청일이 달라지면 다시 확인해주세요."
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260421005400112773', 'ba390000-0000-4000-8024-000000000008');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8024-000000000009', '20260722005400213264', 'future-youth-jobs-2026-may-v1', $rule$
{
  "policyNumber": "20260722005400213264",
  "ruleVersion": "future-youth-jobs-2026-may-v1",
  "scope": "2026년 5월 미래 청년 일자리 참여 조건",
  "reason": "5월 모집 당시의 조건을 답해주세요. 이후 2차 모집에는 적용하지 않아요.",
  "sourceUrl": "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004",
  "questions": [
    {
      "id": "birthRange",
      "label": "공고의 출생일 범위에 해당하나요?",
      "help": "1986.1.1.~2007.12.31. 출생자가 기본 대상이에요. 의무복무 제대군인은 복무기간 1년 미만은 1985년생, 1~2년 미만은 1984년생, 2년 이상은 1983년생까지 연장해요. 적용 여부는 운영사무국에서 확인해주세요.",
      "options": [
        {
          "value": "BASE_RANGE",
          "label": "1986.1.1.~2007.12.31. 출생"
        },
        {
          "value": "EXTENSION_CONFIRMED",
          "label": "기관 확인 · 연장된 연령 기준 충족"
        },
        {
          "value": "TOO_YOUNG",
          "label": "2008.1.1. 이후 출생"
        },
        {
          "value": "OLDER_NOT_ELIGIBLE",
          "label": "1985.12.31.까지 출생 · 연장 불가 또는 연장 상한 초과"
        },
        {
          "value": "EXTENSION_PENDING",
          "label": "군복무에 따른 연령 연장 확인 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "residence",
      "label": "이 모집에 신청할 당시 서울에 주민등록이 되어 있었나요?",
      "help": "서울 소재 대학·직장만으로 거주 조건을 충족하지는 않아요. 등록 형태나 증빙 인정 여부가 불분명하면 운영사무국에서 확인해주세요.",
      "options": [
        {
          "value": "SEOUL",
          "label": "서울 주민등록 확인"
        },
        {
          "value": "OUTSIDE",
          "label": "서울 외 지역 주민등록 확인"
        },
        {
          "value": "UNKNOWN",
          "label": "등록·증빙 확인 중"
        }
      ]
    },
    {
      "id": "employment",
      "label": "신청서 제출일의 근로 상태는 무엇인가요?",
      "help": "근로 중이어도 주 30시간 이하 또는 근로계약기간 3개월 미만이면 참여할 수 있어요. 사업자등록은 아래에서 별도로 확인해요.",
      "options": [
        {
          "value": "NOT_WORKING",
          "label": "근로 중이 아니에요"
        },
        {
          "value": "UP_TO_30_HOURS",
          "label": "주 30시간 이하 근로"
        },
        {
          "value": "UNDER_3_MONTHS",
          "label": "근로계약기간 3개월 미만"
        },
        {
          "value": "OVER_LIMITS",
          "label": "주 30시간 초과 · 계약기간 3개월 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "근로시간·계약기간 확인 중"
        }
      ]
    },
    {
      "id": "education",
      "label": "공고 기준의 대학·대학원 재학 상태는 무엇인가요?",
      "help": "수료·졸업예정·졸업유예와 방송통신·사이버·야간대학(원) 재학생은 예외예요. 졸업예정은 공고일 기준 마지막 학년 2학기 재학 또는 이수 여부를 확인해주세요. 예외 증빙은 학교·운영사무국에서 확인해요.",
      "options": [
        {
          "value": "NOT_ENROLLED",
          "label": "재학·휴학 중이 아니에요"
        },
        {
          "value": "EXCEPTION_CONFIRMED",
          "label": "재학 예외 · 증빙 인정 확인"
        },
        {
          "value": "EXCLUDED_CONFIRMED",
          "label": "재학·휴학 중 · 예외 해당 없음"
        },
        {
          "value": "UNKNOWN",
          "label": "재학 예외 확인 중"
        }
      ]
    },
    {
      "id": "business",
      "label": "신청 당시 사업자등록이 있었나요?",
      "help": "등록이 있어도 휴업 등 실제 미영업을 증명하거나, 근로자·임대사무실이 없는 부동산임대업임을 증명하면 참여할 수 있어요. 증빙 인정 여부는 운영사무국에서 확인해주세요.",
      "options": [
        {
          "value": "NONE",
          "label": "사업자등록 없음"
        },
        {
          "value": "INACTIVE_CONFIRMED",
          "label": "미영업 증빙 인정 확인"
        },
        {
          "value": "RENTAL_EXCEPTION_CONFIRMED",
          "label": "부동산임대업 예외 인정 확인"
        },
        {
          "value": "ACTIVE_NO_EXCEPTION",
          "label": "사업자등록 있음 · 예외 해당 없음"
        },
        {
          "value": "UNKNOWN",
          "label": "사업자등록·예외 확인 중"
        }
      ]
    },
    {
      "id": "publicJob",
      "label": "다른 정부·서울시 일자리 사업에 참여 중이었나요?",
      "help": "서울시 매력일자리·공공근로·지역주도형 청년일자리 등이 해당해요. 교육이나 수당만 받는 경우는 일자리 사업 참여에 해당하는지 운영사무국에서 확인해주세요.",
      "options": [
        {
          "value": "NO",
          "label": "참여하지 않았어요"
        },
        {
          "value": "YES",
          "label": "참여 중이었어요"
        },
        {
          "value": "UNKNOWN",
          "label": "사업 유형·참여 여부 확인 중"
        }
      ]
    }
  ],
  "contentHash": "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea",
  "validFrom": "2026-05-03T15:00:00Z",
  "validUntil": "2026-12-31T15:00:00Z",
  "explanation": "5월 모집의 여섯 조건을 비교한 결과예요. 이후 모집 기준은 별도 확인해주세요.",
  "remainingChecks": [
    "등록·근로계약·재학·사업 예외의 증빙 인정 여부를 운영사무국에서 확인해주세요.",
    "직무별 법정 제한과 근로 가능 여부는 담당 기관에서 확인해요. 민감한 이력이나 증빙은 이곳에 입력하지 않아요.",
    "신청서·동의서·가점 증빙, 필수 교육 참여와 서류·면접 심사는 별도 확인이 필요해요. 실제 선발은 공식 결과를 확인해주세요.",
    "기초생활수급자는 근로소득 발생으로 수급자 지위가 달라질 수 있어요. 참여 전 주민센터에서 상담해주세요."
  ],
  "checks": [
    {
      "label": "공고의 연령 기준",
      "evidence": "1986.1.1.~2007.12.31. 출생자가 기본 대상이에요. 의무복무 제대군인은 복무기간에 따라 1985·1984·1983년생까지 연장하므로 적용 여부를 확인해주세요.",
      "questionId": "birthRange",
      "cases": [
        {
          "when": {
            "birthRange": [
              "BASE_RANGE",
              "EXTENSION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "birthRange": [
              "OLDER_NOT_ELIGIBLE",
              "TOO_YOUNG"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "1986.1.1.~2007.12.31. 출생자가 기본 대상이에요. 의무복무 제대군인은 복무기간에 따라 1985·1984·1983년생까지 연장하므로 적용 여부를 확인해주세요."
    },
    {
      "label": "서울 주민등록",
      "evidence": "주민등록 기준 서울 거주자를 모집해요. 등록 형태와 증빙 인정 여부를 확인해주세요.",
      "questionId": "residence",
      "cases": [
        {
          "when": {
            "residence": [
              "SEOUL"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "residence": [
              "OUTSIDE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "주민등록 기준 서울 거주자를 모집해요. 등록 형태와 증빙 인정 여부를 확인해주세요."
    },
    {
      "label": "근로 상태",
      "evidence": "신청서 제출일 기준 미취업이거나, 주 30시간 이하 또는 계약기간 3개월 미만 근로 예외에 해당해야 해요.",
      "questionId": "employment",
      "cases": [
        {
          "when": {
            "employment": [
              "NOT_WORKING",
              "UNDER_3_MONTHS",
              "UP_TO_30_HOURS"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "employment": [
              "OVER_LIMITS"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "신청서 제출일 기준 미취업이거나, 주 30시간 이하 또는 계약기간 3개월 미만 근로 예외에 해당해야 해요."
    },
    {
      "label": "재학·휴학 제한",
      "evidence": "대학·대학원 재학·휴학은 원칙적으로 제외하지만 수료·졸업예정·졸업유예·방송통신·사이버·야간대학(원) 재학 예외가 있어요. 증빙 인정 여부를 확인해주세요.",
      "questionId": "education",
      "cases": [
        {
          "when": {
            "education": [
              "EXCEPTION_CONFIRMED",
              "NOT_ENROLLED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "education": [
              "EXCLUDED_CONFIRMED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "대학·대학원 재학·휴학은 원칙적으로 제외하지만 수료·졸업예정·졸업유예·방송통신·사이버·야간대학(원) 재학 예외가 있어요. 증빙 인정 여부를 확인해주세요."
    },
    {
      "label": "사업자등록 제한",
      "evidence": "사업자등록이 있으면 실제 미영업이나 근로자·임대사무실 없는 부동산임대업의 증빙 예외를 확인해야 해요.",
      "questionId": "business",
      "cases": [
        {
          "when": {
            "business": [
              "INACTIVE_CONFIRMED",
              "NONE",
              "RENTAL_EXCEPTION_CONFIRMED"
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
      "unknownExplanation": "사업자등록이 있으면 실제 미영업이나 근로자·임대사무실 없는 부동산임대업의 증빙 예외를 확인해야 해요."
    },
    {
      "label": "다른 일자리 사업 참여",
      "evidence": "정부·서울시 일자리 창출 사업 참여자는 제외해요. 해당 사업의 유형과 참여 여부를 확인해주세요.",
      "questionId": "publicJob",
      "cases": [
        {
          "when": {
            "publicJob": [
              "NO"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "publicJob": [
              "YES"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "정부·서울시 일자리 창출 사업 참여자는 제외해요. 해당 사업의 유형과 참여 여부를 확인해주세요."
    }
  ],
  "periodNotice": {
    "opensAt": "2026-05-17T15:00:00Z",
    "closesAt": "2026-05-31T15:00:00Z",
    "before": "접수 전이에요. 신청 기간은 2026년 5월 18일~5월 31일 23:59(서울)이에요. 5월 4일은 공고 시작일이에요.",
    "open": "신청 기간은 2026년 5월 18일~5월 31일 23:59(서울)이에요. 5월 4일은 공고 시작일이에요.",
    "closed": "2026년 5월 모집은 5월 31일 23:59(서울)에 접수가 마감됐어요."
  },
  "birthBinding": {
    "questionId": "birthRange",
    "minimumInclusive": "1986-01-01",
    "maximumInclusive": "2007-12-31",
    "below": "EXTENSION_PENDING",
    "within": "BASE_RANGE",
    "above": "TOO_YOUNG"
  }
}
$rule$::jsonb, 'migration-v24', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v24');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260722005400213264', 'ba390000-0000-4000-8024-000000000009');

-- 원문과 공식 안내를 대조해 검토한 국민취업지원제도·AI학업장려 학자금대출의 질문·판정 규칙이다.
-- 운영 중 규칙 변경은 관리자 화면이나 rules:policy 명령으로 새 버전을 등록한다.
INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8004-000000000001', '20260710005400113256', 'employment-support-2026-v1', $rule$
{
  "policyNumber": "20260710005400113256",
  "ruleVersion": "employment-support-2026-v1",
  "scope": "2026년 국민취업지원제도 참여 조건",
  "reason": "신청일 기준 연령·취업·재학·실업급여·이전 참여와 가구 소득·재산·최근 2년 취업경험으로 참여 가능 여부와 유형을 확인해요. 실제 참여 여부와 유형은 고용센터가 소득·재산 자료로 정해요.",
  "sourceUrl": "https://www.work24.go.kr/ua/z/z/1300/selectEmssRqutIntro.do",
  "contentHash": "4ff2a49d8c1f2796808f9eb7a94b52161339bb9c6957d21fcfdae2d2e6767a49",
  "validFrom": "2026-01-01T00:00:00+09:00",
  "validUntil": "2027-01-01T00:00:00+09:00",
  "explanation": "국민취업지원제도의 참여 제한과 Ⅰ·Ⅱ유형 기준을 비교했어요. 실제 유형은 고용센터 상담과 소득·재산 조사로 정해져요.",
  "remainingChecks": [
    "고용24에 구직등록을 한 뒤 고용24나 고용센터에 신청하고, 제도 안내 영상(1·2회차)을 들어야 해요. 결과는 1개월 안에 통지돼요.",
    "본인 월평균 소득이 1인 가구 기준 중위소득 60%(2026년 153만 8,543원)를 넘거나 생계급여(조건부수급자 제외)를 받으면 Ⅰ유형이 인정되지 않아요.",
    "재정지원 직접일자리나 지자체 구직수당(월 50만 원 이상 또는 총 300만 원 이상)을 받는 중이면 참여할 수 없고, 끝난 지 6개월 안이면 Ⅰ유형이 제한돼요. 군복무 중(전역 2개월 이내 제외)이거나 결혼이민자 가족이 아닌 외국인도 참여할 수 없어요.",
    "선발형은 예산 범위에서 점수로 선발해요. 2026년에는 취업 경험이 없는 청년 3만 명을 Ⅰ유형으로 추가 모집했고 선착순으로 마감될 수 있어요."
  ],
  "questions": [
    {
      "id": "age",
      "label": "취업지원 신청일 기준 만 나이는 어떻게 되나요?",
      "help": "만 15~69세가 참여할 수 있어요. 청년(만 15~34세)은 병역을 마쳤다면 실제 복무 기간(최대 3년)만큼 상한이 늘어나 최대 만 37세까지 청년 기준을 적용해요.",
      "options": [
        {
          "value": "AGE_15_TO_34",
          "label": "만 15~34세"
        },
        {
          "value": "EXTENSION_CONFIRMED",
          "label": "만 35~37세 · 복무 기간을 더하면 청년 기준 이내"
        },
        {
          "value": "AGE_35_TO_69",
          "label": "만 35~69세 · 청년 기준에 해당하지 않아요"
        },
        {
          "value": "EXTENSION_PENDING",
          "label": "만 35세 이상 · 병역 가산 확인 중"
        },
        {
          "value": "UNDER_15",
          "label": "만 15세 미만"
        },
        {
          "value": "OVER_69",
          "label": "만 70세 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "employment",
      "label": "신청일에 일하고 있나요?",
      "help": "임금근로는 주 30시간 미만, 사업소득이나 노무제공(프리랜서) 소득은 월 250만 원 미만이면 불완전 취업자로 참여할 수 있어요. 사업소득이 확인되지 않으면 매출액에 업종별 조정률을 곱해 환산해요.",
      "options": [
        {
          "value": "NOT_WORKING",
          "label": "일하고 있지 않아요"
        },
        {
          "value": "WAGE_UNDER_30H",
          "label": "임금근로 · 주 30시간 미만"
        },
        {
          "value": "INCOME_UNDER_250",
          "label": "사업·노무제공 · 월 소득 250만 원 미만"
        },
        {
          "value": "WAGE_30H_OR_MORE",
          "label": "임금근로 · 주 30시간 이상"
        },
        {
          "value": "BUSINESS_250_OR_MORE",
          "label": "사업자 · 월 소득 250만 원 이상"
        },
        {
          "value": "FREELANCE_250_OR_MORE",
          "label": "노무제공(프리랜서) · 월 소득 250만 원 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "school",
      "label": "학교나 학원에 다니고 있나요?",
      "help": "상급학교 진학이나 전문자격증 취득을 위해 학교에 다니거나 학원에서 수강하면 참여할 수 없어요. 대학(원) 최종 학년 마지막 학기 재학·휴학생과, 일과 병행할 수 있는 야간대학(원)·학점은행제·사이버대·방송통신대(고)·야간고는 참여할 수 있어요.",
      "options": [
        {
          "value": "NOT_ENROLLED",
          "label": "학교·학원에 다니지 않아요"
        },
        {
          "value": "FINAL_SEMESTER",
          "label": "대학(원) 최종 학년 마지막 학기 재학·휴학"
        },
        {
          "value": "FLEXIBLE_SCHOOL",
          "label": "야간·학점은행제·사이버·방송통신 과정 · 일과 병행 가능"
        },
        {
          "value": "HIGH_SCHOOL_GRADUATING",
          "label": "고등학교 졸업예정 · 진학하지 않아요"
        },
        {
          "value": "ON_LEAVE",
          "label": "휴학 중 · 마지막 학기가 아니에요"
        },
        {
          "value": "ENROLLED",
          "label": "재학 중이거나 진학·자격증 준비로 학원 수강 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "unemploymentBenefit",
      "label": "실업급여(구직급여)를 받고 있거나 최근에 받았나요?",
      "help": "실업급여를 받는 동안은 참여할 수 없어요. Ⅰ유형(구직촉진수당)은 마지막으로 받은 날의 다음 날부터 6개월이 지나야 인정될 수 있어요.",
      "options": [
        {
          "value": "NONE",
          "label": "받은 적 없거나 마지막으로 받은 지 6개월이 지났어요"
        },
        {
          "value": "ENDED_WITHIN_6M",
          "label": "마지막으로 받은 지 6개월이 안 됐어요"
        },
        {
          "value": "RECEIVING",
          "label": "지금 받고 있어요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "pastParticipation",
      "label": "국민취업지원제도에 참여한 적이 있나요?",
      "help": "취업지원이 끝나면 원칙적으로 3년이 지나야 다시 신청할 수 있어요. 취·창업으로 끝났으면 취업 기간에 따라 1~2년, 부정한 방법으로 수당을 받았으면 5년이에요.",
      "options": [
        {
          "value": "NEVER",
          "label": "참여한 적 없어요"
        },
        {
          "value": "ENDED_OVER_3Y",
          "label": "취업지원이 끝난 지 3년이 지났어요"
        },
        {
          "value": "ENDED_1_TO_3Y",
          "label": "끝난 지 1년은 지났지만 3년은 안 됐어요"
        },
        {
          "value": "ENDED_WITHIN_1Y",
          "label": "끝난 지 1년이 안 됐어요"
        },
        {
          "value": "PARTICIPATING",
          "label": "지금 참여 중이에요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "householdIncome",
      "label": "가구 월평균 소득은 2026년 기준 중위소득의 몇 %인가요?",
      "help": "가구는 주민등록상 같은 세대의 본인·배우자·부모·자녀예요. 1인 가구는 60% 153만 8,543원·100% 256만 4,238원·120% 307만 7,086원, 4인 가구는 60% 389만 6,843원·100% 649만 4,738원·120% 779만 3,686원이에요. 고용센터는 건강보험 자료 등으로 확인해요.",
      "options": [
        {
          "value": "UP_TO_60",
          "label": "60% 이하"
        },
        {
          "value": "FROM_60_TO_100",
          "label": "60% 초과 100% 이하"
        },
        {
          "value": "FROM_100_TO_120",
          "label": "100% 초과 120% 이하"
        },
        {
          "value": "OVER_120",
          "label": "120% 초과"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "assets",
      "label": "가구원 재산을 합치면 얼마인가요?",
      "help": "가구원이 가진 토지·건물·주택, 임차보증금, 분양권, 승용차를 더하고 그 재산을 얻으며 생긴 빚을 빼요. 예금은 넣지 않아요. 지역별 공제(서울 9,900만 원 등)가 있어 정확한 금액은 고용센터에서 확인해주세요.",
      "options": [
        {
          "value": "UP_TO_4",
          "label": "4억 원 이하"
        },
        {
          "value": "FROM_4_TO_5",
          "label": "4억 원 초과 5억 원 이하"
        },
        {
          "value": "OVER_5",
          "label": "5억 원 초과"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "workExperience",
      "label": "신청일 전 2년 안에 일한 기간을 합치면 100일 또는 800시간 이상인가요?",
      "help": "고용보험 가입 기간, 사업자등록 기간 등을 더하고 겹치는 기간은 한 번만 세요. 근로장학생으로 일한 기간도 포함해요.",
      "options": [
        {
          "value": "YES",
          "label": "100일 또는 800시간 이상"
        },
        {
          "value": "NO",
          "label": "100일·800시간 모두 미만"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "checks": [
    {
      "questionId": "age",
      "label": "신청일 연령",
      "evidence": "취업지원 신청 당시 만 15~69세가 참여할 수 있어요. 청년(만 15~34세)은 병역의무 이행 기간을 실제 복무 기간으로 최대 3년까지 더해요.",
      "cases": [
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "AGE_35_TO_69",
              "EXTENSION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "참여 연령(만 15~69세)에 해당해요."
        },
        {
          "when": {
            "age": [
              "OVER_69",
              "UNDER_15"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "만 15~69세만 참여할 수 있어요."
        }
      ],
      "unknownExplanation": "만 35세 이상은 만 69세까지 참여할 수 있어요. 병역 이행 기간을 더해 청년 기준(최대 만 37세)에 해당하는지, 만 70세 이상인지 확인해주세요."
    },
    {
      "questionId": "employment",
      "label": "취업 상태",
      "evidence": "근로능력과 구직의사가 있지만 취업하지 못한 사람이 대상이에요. 임금근로 주 30시간 미만, 사업소득·노무제공 월 250만 원 미만은 불완전 취업자로 참여할 수 있고, 영세 자영업자(직전 1년 매출 3억 원 이하)는 Ⅱ유형 특정계층으로 참여할 수 있어요.",
      "cases": [
        {
          "when": {
            "employment": [
              "NOT_WORKING"
            ]
          },
          "outcome": "MET",
          "explanation": "취업하지 않은 구직자는 참여할 수 있어요."
        },
        {
          "when": {
            "employment": [
              "INCOME_UNDER_250",
              "WAGE_UNDER_30H"
            ]
          },
          "outcome": "MET",
          "explanation": "불완전 취업자로 더 나은 일자리를 구한다면 참여할 수 있어요."
        },
        {
          "when": {
            "employment": [
              "FREELANCE_250_OR_MORE",
              "WAGE_30H_OR_MORE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "주 30시간 이상 임금근로나 월 소득 250만 원 이상 노무제공은 취업 중으로 보아 참여할 수 없어요."
        },
        {
          "when": {
            "employment": [
              "BUSINESS_250_OR_MORE"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "사업소득이 월 250만 원 이상이면 참여가 제한되지만, 직전 1년 매출 3억 원 이하 영세 자영업자는 Ⅱ유형으로 참여할 수 있어요. 고용센터에서 확인해주세요."
        }
      ],
      "unknownExplanation": "신청일의 근로시간이나 월 소득을 확인해주세요."
    },
    {
      "questionId": "school",
      "label": "재학·수강",
      "evidence": "상급학교 진학·전문자격증 취득을 위해 학교에 재학하거나 학원에서 수강 중인 사람은 참여할 수 없어요. 졸업예정자(대학(원) 최종 학년 마지막 학기 재학·휴학생)와 일과 병행할 수 있는 야간대학(원)·학점은행제·사이버대·방송통신대(고)·야간고는 참여할 수 있어요.",
      "cases": [
        {
          "when": {
            "school": [
              "FINAL_SEMESTER",
              "FLEXIBLE_SCHOOL",
              "NOT_ENROLLED"
            ]
          },
          "outcome": "MET",
          "explanation": "재학·수강 제한에 해당하지 않아요."
        },
        {
          "when": {
            "school": [
              "ENROLLED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "재학 중이거나 진학·자격증 준비로 수강 중이면 참여할 수 없어요. 졸업예정자나 일과 병행할 수 있는 과정은 예외예요."
        },
        {
          "when": {
            "school": [
              "HIGH_SCHOOL_GRADUATING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "고등학교 졸업예정자는 상급학교에 진학하지 않는다는 확인을 거쳐 참여할 수 있어요. 세부 기준은 고용센터에서 확인해주세요."
        },
        {
          "when": {
            "school": [
              "ON_LEAVE"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "마지막 학기가 아닌 휴학생의 참여 기준은 공식 안내에서 확인하지 못했어요. 고용센터에 확인해주세요."
        }
      ],
      "unknownExplanation": "다니는 학교·과정과 졸업 예정 시기를 확인해주세요."
    },
    {
      "questionId": "unemploymentBenefit",
      "label": "실업급여 수급",
      "evidence": "실업급여(구직급여)를 받는 중에는 참여할 수 없어요. Ⅰ유형은 구직급여를 마지막으로 받은 날의 다음 날부터 6개월이 지나야 인정될 수 있어요.",
      "cases": [
        {
          "when": {
            "unemploymentBenefit": [
              "NONE"
            ]
          },
          "outcome": "MET",
          "explanation": "실업급여 수급 제한에 해당하지 않아요."
        },
        {
          "when": {
            "unemploymentBenefit": [
              "ENDED_WITHIN_6M"
            ]
          },
          "outcome": "MET",
          "explanation": "수급이 끝나 참여할 수 있어요. 다만 Ⅰ유형(구직촉진수당)은 마지막으로 받은 날부터 6개월이 지나야 인정될 수 있어요."
        },
        {
          "when": {
            "unemploymentBenefit": [
              "RECEIVING"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "실업급여를 받는 동안은 참여할 수 없어요. 수급이 끝나면 신청할 수 있어요."
        }
      ],
      "unknownExplanation": "실업급여를 받고 있는지, 마지막으로 받은 날이 언제인지 확인해주세요."
    },
    {
      "questionId": "pastParticipation",
      "label": "재참여 제한",
      "evidence": "취업지원이 끝나면 원칙적으로 3년이 지나야 다시 신청할 수 있어요. 취·창업으로 끝나면 취업 기간에 따라 1~2년, 부정수급은 5년이에요.",
      "cases": [
        {
          "when": {
            "pastParticipation": [
              "ENDED_OVER_3Y",
              "NEVER"
            ]
          },
          "outcome": "MET",
          "explanation": "재참여 제한 기간에 해당하지 않아요. 부정한 방법으로 수당을 받아 끝났다면 5년이 지나야 해요."
        },
        {
          "when": {
            "pastParticipation": [
              "ENDED_WITHIN_1Y"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "취업지원이 끝난 날부터 최소 1년이 지나야 다시 신청할 수 있어요."
        },
        {
          "when": {
            "pastParticipation": [
              "PARTICIPATING"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "지금 참여 중이면 새로 신청할 수 없어요."
        },
        {
          "when": {
            "pastParticipation": [
              "ENDED_1_TO_3Y"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "끝난 사유와 취·창업 기간에 따라 1~3년 제한이 달라요. 고용센터에서 확인해주세요."
        }
      ],
      "unknownExplanation": "이전 참여의 취업지원 종료일과 종료 사유를 확인해주세요."
    },
    {
      "questionId": "householdIncome",
      "label": "참여 유형",
      "evidence": "Ⅰ유형 요건심사형은 가구 소득 중위소득 60% 이하·재산 4억 원(청년 5억 원) 이하·최근 2년 취업경험 100일(800시간) 이상이에요. 선발형은 취업경험이 부족하거나 청년(가구 소득 120% 이하·재산 5억 원 이하)일 때 예산 범위에서 선발해요. Ⅱ유형은 청년은 소득·재산과 관계없이, 만 35~69세는 가구 소득 100% 이하면 참여할 수 있어요.",
      "cases": [
        {
          "when": {
            "age": [
              "OVER_69",
              "UNDER_15"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "만 15~69세만 참여할 수 있어 참여 유형을 안내하지 않아요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ],
            "unemploymentBenefit": [
              "ENDED_WITHIN_6M",
              "RECEIVING"
            ]
          },
          "outcome": "MET",
          "explanation": "청년은 소득·재산과 관계없이 Ⅱ유형으로 참여할 수 있어요. 실업급여를 받는 동안은 신청할 수 없고, Ⅰ유형(구직촉진수당)은 마지막으로 받은 날부터 6개월이 지나야 인정될 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "FROM_4_TO_5",
              "UP_TO_4"
            ],
            "workExperience": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 요건심사형 기준에 해당해요. 구직촉진수당 월 60만 원(부양가족 1인당 10만 원, 최대 40만 원 추가)을 최대 6개월 받을 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "FROM_4_TO_5",
              "UP_TO_4"
            ],
            "workExperience": [
              "NO"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 선발형(청년 특례) 기준에 해당해요. 예산 범위에서 점수로 선발하고(취업경험은 점수에 반영), 선발되지 않으면 Ⅱ유형으로 참여해요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "FROM_4_TO_5",
              "UP_TO_4"
            ],
            "workExperience": [
              "",
              "UNKNOWN"
            ]
          },
          "outcome": "MET",
          "explanation": "최근 2년 취업경험이 100일(800시간) 이상이면 Ⅰ유형 요건심사형, 아니면 Ⅰ유형 선발형(청년 특례) 기준에 해당해요. 선발형은 예산 범위에서 선발하고, 선발되지 않으면 Ⅱ유형으로 참여해요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "FROM_100_TO_120",
              "FROM_60_TO_100"
            ],
            "assets": [
              "FROM_4_TO_5",
              "UP_TO_4"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 선발형(청년 특례) 기준에 해당해요. 예산 범위에서 점수로 선발하고(취업경험은 점수에 반영), 선발되지 않으면 Ⅱ유형으로 참여해요."
        },
        {
          "when": {
            "age": [
              "AGE_15_TO_34",
              "EXTENSION_CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "청년은 소득·재산과 관계없이 Ⅱ유형으로 참여할 수 있어요. Ⅰ유형은 최근 6개월 안에 실업급여를 받지 않았고 가구 소득이 중위소득 120% 이하·재산 5억 원 이하면 선발형, 60% 이하이면서 최근 2년 취업경험도 있으면 요건심사형으로 인정될 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "unemploymentBenefit": [
              "ENDED_WITHIN_6M",
              "RECEIVING"
            ],
            "householdIncome": [
              "FROM_60_TO_100",
              "UP_TO_60"
            ]
          },
          "outcome": "MET",
          "explanation": "가구 소득이 중위소득 100% 이하라 Ⅱ유형으로 참여할 수 있어요. 실업급여를 받는 동안은 신청할 수 없고, Ⅰ유형(구직촉진수당)은 마지막으로 받은 날부터 6개월이 지나야 인정될 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "UP_TO_4"
            ],
            "workExperience": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 요건심사형 기준에 해당해요. 구직촉진수당 월 60만 원(부양가족 1인당 10만 원, 최대 40만 원 추가)을 최대 6개월 받을 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "UP_TO_4"
            ],
            "workExperience": [
              "NO"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 선발형(취업경험 부족) 기준에 해당해요. 예산 범위에서 점수로 선발하고, 선발되지 않으면 Ⅱ유형으로 참여해요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "UP_TO_4"
            ],
            "workExperience": [
              "",
              "UNKNOWN"
            ]
          },
          "outcome": "MET",
          "explanation": "최근 2년 취업경험이 100일(800시간) 이상이면 Ⅰ유형 요건심사형, 아니면 Ⅰ유형 선발형 기준에 해당해요. 선발되지 않으면 Ⅱ유형으로 참여해요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "householdIncome": [
              "FROM_60_TO_100",
              "UP_TO_60"
            ]
          },
          "outcome": "MET",
          "explanation": "가구 소득이 중위소득 100% 이하라 Ⅱ유형으로 참여할 수 있어요. Ⅰ유형은 최근 6개월 안에 실업급여를 받지 않았고 가구 소득 60% 이하·재산 4억 원 이하일 때 인정될 수 있어요."
        },
        {
          "when": {
            "age": [
              "AGE_35_TO_69"
            ],
            "householdIncome": [
              "FROM_100_TO_120",
              "OVER_120"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "만 35~69세는 가구 소득이 중위소득 100%를 넘으면 특정계층(영세 자영업자·한부모·건설일용직·저임금 실직자 등)일 때만 참여할 수 있어요. 해당 여부를 고용센터에서 확인해주세요."
        },
        {
          "when": {
            "age": [
              "",
              "EXTENSION_PENDING",
              "UNKNOWN"
            ],
            "unemploymentBenefit": [
              "ENDED_WITHIN_6M",
              "RECEIVING"
            ],
            "householdIncome": [
              "FROM_60_TO_100",
              "UP_TO_60"
            ]
          },
          "outcome": "MET",
          "explanation": "가구 소득이 중위소득 100% 이하라 청년·중장년 모두 Ⅱ유형으로 참여할 수 있어요. 실업급여를 받는 동안은 신청할 수 없고, Ⅰ유형(구직촉진수당)은 마지막으로 받은 날부터 6개월이 지나야 인정될 수 있어요."
        },
        {
          "when": {
            "age": [
              "",
              "EXTENSION_PENDING",
              "UNKNOWN"
            ],
            "unemploymentBenefit": [
              "NONE"
            ],
            "householdIncome": [
              "UP_TO_60"
            ],
            "assets": [
              "UP_TO_4"
            ],
            "workExperience": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "Ⅰ유형 요건심사형 기준에 해당해요. 구직촉진수당 월 60만 원(부양가족 1인당 10만 원, 최대 40만 원 추가)을 최대 6개월 받을 수 있어요."
        },
        {
          "when": {
            "age": [
              "",
              "EXTENSION_PENDING",
              "UNKNOWN"
            ],
            "householdIncome": [
              "FROM_60_TO_100",
              "UP_TO_60"
            ]
          },
          "outcome": "MET",
          "explanation": "가구 소득이 중위소득 100% 이하라 Ⅱ유형으로 참여할 수 있어요. Ⅰ유형 기준은 청년(병역 가산 포함)인지와 최근 6개월 안의 실업급여 수급 여부에 따라 달라요."
        },
        {
          "when": {
            "age": [
              "",
              "EXTENSION_PENDING",
              "UNKNOWN"
            ],
            "householdIncome": [
              "FROM_100_TO_120",
              "OVER_120"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "청년(병역 가산 포함)이면 Ⅱ유형으로 참여할 수 있지만, 만 35~69세는 가구 소득이 중위소득 100%를 넘으면 특정계층일 때만 참여할 수 있어요. 연령 구간을 확인해주세요."
        }
      ],
      "unknownExplanation": "연령과 가구 소득·재산·최근 2년 취업경험을 답하면 참여 유형을 안내해요. 실제 유형은 고용센터가 소득·재산 자료로 정해요.",
      "providedAnswers": [
        {
          "questionId": "householdIncome",
          "prefix": "가구 소득 "
        },
        {
          "questionId": "assets",
          "prefix": "재산 "
        },
        {
          "questionId": "workExperience",
          "prefix": "취업경험 "
        }
      ],
      "separator": " · "
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
  "ageNotice": "오늘(서울 기준) 신청 시 연령이에요. 병역을 마쳤다면 실제 복무 기간(최대 3년)만큼 청년 상한이 늘어나요."
}
$rule$::jsonb, 'seed', '검토한 공고 규칙 초기 데이터', now(), 'seed');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260710005400113256', 'ba390000-0000-4000-8004-000000000001');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8004-000000000002', '20260903005400113371', 'ai-study-loan-2026-2-v1', $rule$
{
  "policyNumber": "20260903005400113371",
  "ruleVersion": "ai-study-loan-2026-2-v1",
  "scope": "2026학년도 2학기 AI학업장려 학자금대출 신청 조건",
  "reason": "대출 신청일 기준 연령·국적·대상학과·학적·직전학기 이수학점을 확인해요. 소득(학자금 지원구간)과 신용은 보지 않아요. 대상학과는 한국장학재단 조회로 먼저 확인해주세요.",
  "sourceUrl": "https://www.kosaf.go.kr/ko/tuition.do?pg=tuition19&ttab1=1",
  "contentHash": "1998a3b594bc6159b162f02d8286cb14d9f1642e83aeaf5bf395d2576b43aeed",
  "validFrom": "2026-07-01T00:00:00+09:00",
  "validUntil": "2026-11-18T00:00:00+09:00",
  "explanation": "AI학업장려 학자금대출의 신청 자격을 비교했어요. 대출 실행 전 대학 등록 확인과 재단 심사가 남아 있어요.",
  "remainingChecks": [
    "대학 등록(등록금 납부 또는 등록금 대출)이 확인돼야 대출이 실행돼요. 실행은 11월 18일 17시에 끝나요.",
    "성실사용계획서를 내야 하고, 12월에 사용 결과보고서를 제출해야 해요. 대상학과는 학기마다 바뀔 수 있어요.",
    "학자금 중복지원자, 갚아야 할 등록금대출을 2만 원 넘게 갚지 않은 사람, 부실자료 제출로 제한 중인 사람, 금융거래 안심차단 등록자는 대출이 제한돼요."
  ],
  "periodNotice": {
    "opensAt": "2026-07-01T09:00:00+09:00",
    "closesAt": "2026-11-17T18:00:00+09:00",
    "before": "신청 전이에요. 2026학년도 2학기 신청 기간은 7월 1일 9시부터 11월 17일 18시까지예요.",
    "open": "2026학년도 2학기 신청은 11월 17일 18시에 마감돼요.",
    "closed": "2026학년도 2학기 신청은 11월 17일 18시에 마감됐어요. 다음 학기 운영은 한국장학재단 안내를 확인해주세요."
  },
  "questions": [
    {
      "id": "age",
      "label": "대출 신청일 기준 만 나이는 어떻게 되나요?",
      "help": "대출 신청일 현재 만 35세 이하 학부생이 대상이에요. 전문대학 계약학과(채용조건형)·선취업 후진학자·중소기업 재직자로 성인·평생교육 학위과정에 다니면 만 45세까지 신청할 수 있어요.",
      "options": [
        {
          "value": "UP_TO_35",
          "label": "만 35세 이하"
        },
        {
          "value": "EXCEPTION_CONFIRMED",
          "label": "만 36~45세 · 계약학과·선취업 후진학·중소기업 재직자 과정"
        },
        {
          "value": "EXCEPTION_PENDING",
          "label": "만 36세 이상 · 예외 해당 여부 확인 중"
        },
        {
          "value": "OVER_LIMIT",
          "label": "만 36세 이상 · 예외 해당 없음 또는 만 46세 이상"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "nationality",
      "label": "대한민국 국민인가요?",
      "help": "해외이주 신고자·영주권자·재외국민과 외국인은 대출을 받을 수 없어요. 해외이주를 포기하거나 영주귀국 후 주민등록을 했다면 신청할 수 있어요.",
      "options": [
        {
          "value": "YES",
          "label": "대한민국 국민이에요"
        },
        {
          "value": "EXCLUDED",
          "label": "외국인·해외이주 신고자·영주권자·재외국민이에요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "department",
      "label": "소속 학과(주전공)가 이번 학기 AI학업장려 대상학과인가요?",
      "help": "AI·SW중심대학과 AI거점대학이 지정해 한국장학재단에 등록한 학과만 대상이에요. 학과 이름만으로는 알 수 없으니 재단 누리집의 ‘대상학과 조회’에서 확인해주세요. 부산대·전남대·충남대는 모든 학과가 대상이에요. 복수전공·부전공은 인정하지 않아요.",
      "options": [
        {
          "value": "TARGET",
          "label": "대상학과로 확인했어요"
        },
        {
          "value": "HUB_UNIVERSITY",
          "label": "부산대·전남대·충남대 학부생이에요"
        },
        {
          "value": "NOT_TARGET",
          "label": "대상학과가 아니에요"
        },
        {
          "value": "UNKNOWN",
          "label": "아직 확인하지 않았어요"
        }
      ]
    },
    {
      "id": "enrollment",
      "label": "2026학년도 2학기 학적은 어떻게 되나요?",
      "help": "국내 대학 학부의 재학생과 입학·복학 예정자가 대상이에요. 대학원생, 졸업유예자, 학점은행제 학습자는 대상이 아니고, 등록하지 않은 휴학생은 대출을 받을 수 없어요.",
      "options": [
        {
          "value": "UNDERGRADUATE",
          "label": "학부 재학·입학·복학 예정"
        },
        {
          "value": "UNREGISTERED_LEAVE",
          "label": "등록하지 않고 휴학 중"
        },
        {
          "value": "GRADUATION_DEFERRED",
          "label": "졸업유예 중"
        },
        {
          "value": "GRADUATE_SCHOOL",
          "label": "대학원생"
        },
        {
          "value": "CREDIT_BANK",
          "label": "학점은행제 학습자"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "credits",
      "label": "직전학기에 이수한 학점은 몇 학점인가요?",
      "help": "F학점과 취소·포기한 과목은 빼요. 성적은 보지 않아요. 소속 대학의 학기당 최소 이수학점이 12학점보다 적으면 대학 학사규정을 따라요.",
      "options": [
        {
          "value": "AT_LEAST_12",
          "label": "12학점 이상"
        },
        {
          "value": "SCHOOL_MINIMUM",
          "label": "12학점 미만 · 대학 최소 이수학점은 채웠어요"
        },
        {
          "value": "BELOW_12",
          "label": "12학점 미만"
        },
        {
          "value": "FIRST_SEMESTER",
          "label": "신입·편입·재입학 후 첫 학기예요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "creditException",
      "label": "이수학점 기준의 예외에 해당하나요?",
      "help": "졸업학년 학부생과 장애인등록증이 있는 학생은 이수학점 기준을 적용하지 않아요. 직전학기 6학점 이상이면 특별승인 교육을 이수해 총 2회까지 승인받을 수 있어요.",
      "options": [
        {
          "value": "GRADUATING_YEAR",
          "label": "졸업학년이에요"
        },
        {
          "value": "DISABILITY",
          "label": "장애인등록증이 있어요"
        },
        {
          "value": "SPECIAL_APPROVAL",
          "label": "특별승인 대상이에요"
        },
        {
          "value": "NONE",
          "label": "해당 없어요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "checks": [
    {
      "questionId": "age",
      "label": "신청일 연령",
      "evidence": "대출 신청일 현재 만 35세 이하 학부생이 대상이에요. 전문대학 계약학과(채용조건형)·선취업 후진학자·중소기업 재직자가 성인·평생교육 학위과정에 다니면 만 45세 이하예요.",
      "cases": [
        {
          "when": {
            "age": [
              "EXCEPTION_CONFIRMED",
              "UP_TO_35"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "age": [
              "OVER_LIMIT"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "만 36세 이상은 계약학과·선취업 후진학자·중소기업 재직자 예외에 해당하면 만 45세까지 신청할 수 있어요. 해당 여부를 확인해주세요."
    },
    {
      "questionId": "nationality",
      "label": "국적",
      "evidence": "대한민국 국민이 대상이에요. 주민등록상 해외이주 신고자·영주권자·재외국민과 외국인은 제외해요.",
      "cases": [
        {
          "when": {
            "nationality": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "nationality": [
              "EXCLUDED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "국적과 해외이주 신고·영주권·재외국민 여부를 확인해주세요."
    },
    {
      "questionId": "department",
      "label": "대상학과",
      "evidence": "AI·SW중심대학과 AI거점대학이 지정해 재단에 등록한 대상학과의 학부생이 대상이에요(주전공 기준). 부산대·전남대·충남대는 전체 학과가 대상이에요.",
      "cases": [
        {
          "when": {
            "department": [
              "HUB_UNIVERSITY",
              "TARGET"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "department": [
              "NOT_TARGET"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "한국장학재단의 ‘대상학과 조회’에서 소속 학과(주전공)가 이번 학기 대상인지 확인해주세요."
    },
    {
      "questionId": "enrollment",
      "label": "학적",
      "evidence": "국내 고등교육기관(학점은행제 기관 제외)의 학부 재학생과 입학·복학 예정자가 대상이에요. 졸업유예자는 대출을 받을 수 없고, 대학 등록이 확인돼야 대출이 실행돼요.",
      "cases": [
        {
          "when": {
            "enrollment": [
              "UNDERGRADUATE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "enrollment": [
              "CREDIT_BANK",
              "GRADUATE_SCHOOL",
              "GRADUATION_DEFERRED",
              "UNREGISTERED_LEAVE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이번 학기 학적과 등록 여부를 확인해주세요."
    },
    {
      "questionId": "credits",
      "label": "직전학기 이수학점",
      "evidence": "직전학기 12학점 이상 이수가 기준이에요(성적 무관). 신입·편입·재입학생의 첫 학기, 졸업학년, 장애인은 적용하지 않고, 특별승인도 인정해요.",
      "cases": [
        {
          "when": {
            "credits": [
              "AT_LEAST_12",
              "FIRST_SEMESTER",
              "SCHOOL_MINIMUM"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "creditException": [
              "DISABILITY",
              "GRADUATING_YEAR",
              "SPECIAL_APPROVAL"
            ]
          },
          "outcome": "MET",
          "explanation": "이수학점 기준의 예외에 해당한다는 답변을 반영했어요."
        },
        {
          "when": {
            "credits": [
              "BELOW_12"
            ],
            "creditException": [
              "NONE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "직전학기 이수학점이 기준에 맞지 않고 예외에도 해당하지 않아요."
        }
      ],
      "unknownExplanation": "직전학기 이수학점과 졸업학년·장애인·특별승인 예외 여부를 확인해주세요.",
      "providedAnswers": [
        {
          "questionId": "credits",
          "prefix": ""
        },
        {
          "questionId": "creditException",
          "prefix": "예외 "
        }
      ],
      "separator": " · "
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 0,
    "maximumInclusive": 35,
    "referenceDate": null,
    "below": "UP_TO_35",
    "within": "UP_TO_35",
    "above": "EXCEPTION_PENDING",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 대출을 신청할 때의 연령이에요."
}
$rule$::jsonb, 'seed', '검토한 공고 규칙 초기 데이터', now(), 'seed');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260903005400113371', 'ba390000-0000-4000-8004-000000000002');

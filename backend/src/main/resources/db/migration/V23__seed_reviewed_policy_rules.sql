INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8000-000000000001', '20260527005400113224', 'exam-fee-2026-v1', $rule$
{
  "policyNumber": "20260527005400113224",
  "ruleVersion": "exam-fee-2026-v1",
  "scope": "2026년 청년 국가기술자격 응시료 지원 조건",
  "reason": "출생일·시험 종류·남은 지원 횟수를 확인해요. 시험 응시자격, 예산 소진 여부, 할인 적용 여부는 큐넷에서 확인해주세요.",
  "sourceUrl": "https://hrdc.hrdkorea.or.kr/hrdc/196105",
  "questions": [
    {
      "id": "birthRange",
      "label": "출생일이 어느 구간에 해당하나요?",
      "help": "오늘의 만 나이 대신 2026년 공식 안내의 출생일 기준을 사용해요. 생년월일 전체는 입력하지 않아요.",
      "options": [
        {
          "value": "ON_OR_AFTER_1991_01_01",
          "label": "1991.1.1. 이후 출생 (당일 포함)"
        },
        {
          "value": "BEFORE_1991_01_01",
          "label": "1990.12.31. 이전 출생 (당일 포함)"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "exam",
      "label": "응시할 시험의 종류와 시행기관을 확인했나요?",
      "help": "한국산업인력공단에서 시행하는 국가기술자격시험이 대상이에요. 큐넷에 보이는 모든 시험이 대상인 것은 아니에요.",
      "options": [
        {
          "value": "HRDK_TECHNICAL",
          "label": "한국산업인력공단 국가기술자격시험"
        },
        {
          "value": "OTHER",
          "label": "다른 기관 시험·국가전문·민간자격"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "remainingUses",
      "label": "큐넷에서 확인한 2026년 남은 응시료 지원 횟수는 얼마인가요?",
      "help": "취소 후 횟수 복구가 아직 반영되지 않았다면 ‘복구 확인 중’을 선택해주세요. 시험에 가지 않은 것만으로는 횟수가 복구되지 않아요.",
      "options": [
        {
          "value": "ONE",
          "label": "1회"
        },
        {
          "value": "TWO",
          "label": "2회"
        },
        {
          "value": "THREE",
          "label": "3회"
        },
        {
          "value": "ZERO",
          "label": "0회 · 복구 대기 없음"
        },
        {
          "value": "RESTORING",
          "label": "접수 취소 후 지원 횟수 복구 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "7d7880c52f4f696225afd12d0871c41bf155ab568dd7b72f3d34b7f76b800d56",
  "validFrom": "2026-01-01T00:00:00+09:00",
  "validUntil": "2027-01-01T00:00:00+09:00",
  "explanation": "출생일·시험 종류·남은 지원 횟수의 확인 결과예요. 시험 접수, 예산 소진 여부, 할인 적용 여부는 별도로 확인해주세요.",
  "remainingChecks": [
    "해당 시험의 응시자격·원서접수 일정은 별도로 확인해주세요.",
    "지원은 2026년 1월 6일부터 예산 소진 전까지예요. 남은 횟수가 있어도 예산 소진 시 지원되지 않으며, 이 서비스는 현재 예산을 조회하지 않아요.",
    "공식 안내는 원서접수 때 지원 자동 적용과 ‘지원받지 않기’ 선택을 설명해요. 결제 전 실제 할인 금액과 횟수 차감을 큐넷에서 확인해주세요."
  ],
  "checks": [
    {
      "label": "공식 출생일 기준",
      "evidence": "2026년 지원 대상은 1991년 1월 1일 이후 출생자예요(당일 포함).",
      "questionId": "birthRange",
      "cases": [
        {
          "when": {
            "birthRange": [
              "ON_OR_AFTER_1991_01_01"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "birthRange": [
              "BEFORE_1991_01_01"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "시험 종류와 시행기관",
      "evidence": "이 지원은 한국산업인력공단이 시행하는 국가기술자격시험의 응시료에 적용돼요.",
      "questionId": "exam",
      "cases": [
        {
          "when": {
            "exam": [
              "HRDK_TECHNICAL"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "exam": [
              "OTHER"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "2026년 남은 지원 횟수",
      "evidence": "해당 연도 최대 3회예요. 원서접수 취소 후 차감 횟수는 복구되지만 시험 미응시만으로는 복구되지 않아요. 실제 반영 여부는 큐넷에서 확인해요.",
      "questionId": "remainingUses",
      "cases": [
        {
          "when": {
            "remainingUses": [
              "ONE",
              "TWO",
              "THREE"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "remainingUses": [
              "ZERO"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        },
        {
          "when": {
            "remainingUses": [
              "RESTORING"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "큐넷에서 지원 횟수가 복구됐는지 확인한 뒤 다시 답해주세요."
        }
      ],
      "unknownExplanation": "이 항목을 확인한 뒤 다시 답해주세요."
    }
  ],
  "birthBinding": {
    "questionId": "birthRange",
    "minimumInclusive": "1991-01-01",
    "maximumInclusive": null,
    "below": "BEFORE_1991_01_01",
    "within": "ON_OR_AFTER_1991_01_01",
    "above": null
  }
}
$rule$::jsonb, 'migration-v23', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v23');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260527005400113224', 'ba390000-0000-4000-8000-000000000001');

INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8000-000000000002', '20260821005400113348', 'work-study-2026-2-v1', $rule$
{
  "policyNumber": "20260821005400113348",
  "ruleVersion": "work-study-2026-2-v1",
  "scope": "2026년 2학기 국가근로장학금 신청 조건",
  "reason": "국적·학적·성적·학자금 지원구간을 확인해요. 대학별 선발요건과 참여 제한은 별도로 확인해주세요.",
  "sourceUrl": "https://www.kosaf.go.kr/ko/scholar.do?pg=scholarship05_04_01",
  "questions": [
    {
      "id": "nationality",
      "label": "대한민국 국적을 가지고 있나요?",
      "help": "국적과 거주지는 다른 조건이에요.",
      "options": [
        {
          "value": "YES",
          "label": "예"
        },
        {
          "value": "NO",
          "label": "아니요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "enrollment",
      "label": "2026년 2학기 지원 대상 대학의 재학생 또는 입학예정자인가요?",
      "help": "학교명이나 학번은 입력하지 않아요. 지원 대상 여부는 대학 안내에서 확인해주세요.",
      "options": [
        {
          "value": "YES",
          "label": "예"
        },
        {
          "value": "NO",
          "label": "아니요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "grade",
      "label": "직전학기 성적은 100점 기준으로 몇 점인가요?",
      "help": "대학이 제공한 백분위 성적을 확인해주세요. 4.5점 만점 평점을 직접 환산하지 마세요.",
      "options": [
        {
          "value": "AT_LEAST_70",
          "label": "70점 이상"
        },
        {
          "value": "BELOW_70",
          "label": "70점 미만"
        },
        {
          "value": "NOT_ISSUED",
          "label": "직전학기 성적이 없어요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "gradeException",
      "label": "성적 기준의 적용 제외를 확인받았나요?",
      "help": "성적 기준을 적용받지 않는 대상인지 재단이나 대학에서 확인해주세요. 사유·증빙은 이곳에 제출하지 않아요.",
      "options": [
        {
          "value": "CONFIRMED",
          "label": "적용 제외 · 재단·대학 확인 완료"
        },
        {
          "value": "NONE",
          "label": "적용 제외 대상 아님"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "income",
      "label": "한국장학재단에서 확인한 2026년 2학기 학자금 지원구간은 몇 구간인가요?",
      "help": "월급이나 가구 소득액으로 직접 환산하지 않아요.",
      "options": [
        {
          "value": "UP_TO_9",
          "label": "기초·차상위 또는 1~9구간"
        },
        {
          "value": "ABOVE_9",
          "label": "9구간 초과"
        },
        {
          "value": "NOT_CALCULATED",
          "label": "아직 산정되지 않았어요"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "incomeException",
      "label": "학자금 지원구간 기준의 적용 제외를 확인받았나요?",
      "help": "위기가구·일부 근로유형은 예외가 있어요. 재단이나 대학에서 적용 제외 여부를 확인해주세요.",
      "options": [
        {
          "value": "CONFIRMED",
          "label": "적용 제외 · 재단·대학 확인 완료"
        },
        {
          "value": "NONE",
          "label": "적용 제외 대상 아님"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    }
  ],
  "contentHash": "a1523aaa7fc8ac8097c70cf4830048c9c6f8430464864823206778f7c82c5e5d",
  "validFrom": "2026-01-01T00:00:00+09:00",
  "validUntil": "2027-01-01T00:00:00+09:00",
  "explanation": "국적·학적·성적·학자금 지원구간의 확인 결과예요. 대학별 기준과 예외는 별도로 확인해주세요.",
  "remainingChecks": [
    "대학의 2026년 2학기 선발요건·참여 제한·중복 참여 기준을 확인해주세요.",
    "대학이 이번 차수에 신청을 받는지, 서류 제출·가구원 동의 기한은 언제인지 확인해주세요."
  ],
  "checks": [
    {
      "label": "대한민국 국적",
      "evidence": "지원 대상은 대한민국 국적 보유자예요.",
      "questionId": "nationality",
      "cases": [
        {
          "when": {
            "nationality": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변이 이 요건에 해당해요."
        },
        {
          "when": {
            "nationality": [
              "NO"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변이 이 요건에 해당하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인해야 해요."
    },
    {
      "label": "지원 대상 대학의 학적",
      "evidence": "지원 대상 대학의 재학생과 입학예정자를 확인해요.",
      "questionId": "enrollment",
      "cases": [
        {
          "when": {
            "enrollment": [
              "YES"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변이 이 요건에 해당해요."
        },
        {
          "when": {
            "enrollment": [
              "NO"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변이 이 요건에 해당하지 않아요."
        }
      ],
      "unknownExplanation": "이 항목을 확인해야 해요."
    },
    {
      "label": "직전학기 성적",
      "evidence": "직전학기 백분위 70점 이상이 기준이며, 인정된 성적 적용 제외를 함께 확인해요.",
      "questionId": "grade",
      "cases": [
        {
          "when": {
            "grade": [
              "AT_LEAST_70"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 구간은 이 기준을 충족해요."
        },
        {
          "when": {
            "gradeException": [
              "CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "재단이나 대학에서 적용 제외를 확인받았다는 답변을 반영했어요."
        },
        {
          "when": {
            "grade": [
              "BELOW_70"
            ],
            "gradeException": [
              "NONE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 구간은 지원 기준에 맞지 않고, 적용 제외 대상에도 해당하지 않아요."
        }
      ],
      "unknownExplanation": "성적·학자금 지원구간이나 적용 제외 여부를 확인한 뒤 다시 답해주세요."
    },
    {
      "label": "학자금 지원구간",
      "evidence": "2026년 2학기 재단 산정 9구간 이하가 기준이며, 인정된 적용 제외를 함께 확인해요.",
      "questionId": "income",
      "cases": [
        {
          "when": {
            "income": [
              "UP_TO_9"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 구간은 이 기준을 충족해요."
        },
        {
          "when": {
            "incomeException": [
              "CONFIRMED"
            ]
          },
          "outcome": "MET",
          "explanation": "재단이나 대학에서 적용 제외를 확인받았다는 답변을 반영했어요."
        },
        {
          "when": {
            "income": [
              "ABOVE_9"
            ],
            "incomeException": [
              "NONE"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 구간은 지원 기준에 맞지 않고, 적용 제외 대상에도 해당하지 않아요."
        }
      ],
      "unknownExplanation": "성적·학자금 지원구간이나 적용 제외 여부를 확인한 뒤 다시 답해주세요."
    }
  ],
  "birthBinding": null
}
$rule$::jsonb, 'migration-v23', '기존 검토 규칙을 데이터로 이전', now(), 'migration-v23');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260821005400113348', 'ba390000-0000-4000-8000-000000000002');


-- 원문과 교육부 사업 공고·계획을 대조해 검토한 청년도약 인재양성 부트캠프의 공통 참여 조건 질문·판정 규칙이다.
-- 운영 중 규칙 변경은 관리자 화면이나 rules:policy 명령으로 새 버전을 등록한다.
INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason, published_at, published_by)
VALUES ('ba390000-0000-4000-8006-000000000001', '20260714005400113259', 'youth-leap-bootcamp-2026-v1', $rule$
{
  "policyNumber": "20260714005400113259",
  "ruleVersion": "youth-leap-bootcamp-2026-v1",
  "scope": "2026년 청년도약 인재양성 부트캠프 공통 참여 조건",
  "reason": "신청일 기준 연령과 일자리·대학 재학 여부로 공통 조건을 확인해요. 병역 연장·휴학 처리·단시간 근로 기준과 지원금은 대학마다 달라 모집 공고를 함께 확인해주세요.",
  "sourceUrl": "https://www.2030db.go.kr/user/ntt/BBS_0000000000000001/NTT_0000000000000674/selectNttDetail.do",
  "contentHash": "b2050ee4e34c23b558851d696404849eec940ec3dbd126844d89134fc0a6c28d",
  "validFrom": "2026-07-07T00:00:00+09:00",
  "validUntil": "2027-03-01T00:00:00+09:00",
  "explanation": "청년도약 인재양성 부트캠프의 공통 참여 조건을 비교했어요. 대학·과정별 모집 조건과 선발은 따로 확인해주세요.",
  "remainingChecks": [
    "첨단산업 인재양성 통합관리 플랫폼(NAIS)에 가입해 등재되고, 고용24 ‘구직준비도’ 사전 검사를 해야 참여할 수 있어요.",
    "병역 연장, 휴학·졸업유예·졸업예정자 처리, 단시간 근로·사업자 기준, 지원금은 대학·과정마다 달라요. 신청하려는 대학의 모집 공고를 확인해주세요.",
    "모집은 대학·기수별로 따로 열리고 선착순이나 서류·면접으로 선발해요."
  ],
  "questions": [
    {
      "id": "age",
      "label": "신청일 기준 만 나이는 어떻게 되나요?",
      "help": "19세 이상 34세 이하 청년이 대상이에요. 병역을 마치면 연령 상한을 늘려주는 대학이 있지만 기준이 대학마다 달라요.",
      "options": [
        {
          "value": "AGE_19_TO_34",
          "label": "만 19~34세"
        },
        {
          "value": "EXTENSION_PENDING",
          "label": "만 35세 이상 · 병역 연장 확인 중"
        },
        {
          "value": "OVER_LIMIT",
          "label": "만 35세 이상 · 병역 연장 해당 없음"
        },
        {
          "value": "UNDER_19",
          "label": "만 19세 미만"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "work",
      "label": "지금 일하고 있나요?",
      "help": "실업·쉬는 중·취업 준비 중인 ‘일자리 밖 청년’이 대상이에요. 단시간 근로를 미취업으로 보는 기준(주 30시간 미만, 주 15시간 미만 등)과 사업자 예외는 대학마다 달라요.",
      "options": [
        {
          "value": "NOT_WORKING",
          "label": "일하지 않아요 · 실업·쉬는 중·취업 준비"
        },
        {
          "value": "PART_TIME",
          "label": "주 30시간 미만 근로 중 · 고용보험 가입 포함"
        },
        {
          "value": "BUSINESS",
          "label": "사업자등록이 있어요"
        },
        {
          "value": "EMPLOYED",
          "label": "주 30시간 이상 일하며 재직 중"
        },
        {
          "value": "UNKNOWN",
          "label": "모르겠어요"
        }
      ]
    },
    {
      "id": "school",
      "label": "대학에 다니고 있나요?",
      "help": "대학 재학생은 참여할 수 없어요. 졸업생과 1년 이상 장기 휴학생·졸업유예생은 공식 안내의 추천 대상이지만, 휴학·졸업유예·졸업예정자를 받는 기준은 대학마다 달라요.",
      "options": [
        {
          "value": "NOT_ENROLLED",
          "label": "다니지 않아요 · 졸업·고졸·중퇴 등"
        },
        {
          "value": "LONG_LEAVE",
          "label": "1년 이상 휴학 중"
        },
        {
          "value": "LONG_DEFERRAL",
          "label": "1년 이상 졸업유예 중"
        },
        {
          "value": "SHORT_LEAVE_OR_DEFERRAL",
          "label": "1년 미만 휴학·졸업유예 중"
        },
        {
          "value": "GRADUATING",
          "label": "졸업예정이에요"
        },
        {
          "value": "GRADUATE_SCHOOL",
          "label": "대학원에 다녀요"
        },
        {
          "value": "ENROLLED",
          "label": "대학에 재학 중이에요"
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
      "evidence": "19세 이상 34세 이하의 일자리 밖 청년이 대상이에요. 공통 안내에는 병역 연장이 없고, 일부 대학은 군 복무기간만큼 연령 상한을 늘려요.",
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
              "OVER_LIMIT",
              "UNDER_19"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "입력한 답변은 이 조건을 충족하지 않아요."
        }
      ],
      "unknownExplanation": "만 35세 이상은 병역을 마쳤다면 대학에 따라 연령 상한이 늘어날 수 있어요. 신청하려는 대학의 모집 공고를 확인해주세요."
    },
    {
      "questionId": "work",
      "label": "일자리 밖 청년",
      "evidence": "실업자, ‘쉬었음’, 취업준비자 등 일자리 밖 청년이 대상이에요. 단시간 근로·사업자 기준은 대학마다 달라요.",
      "cases": [
        {
          "when": {
            "work": [
              "NOT_WORKING"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "work": [
              "EMPLOYED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "재직 중이면 일자리 밖 청년에 해당하지 않아요."
        },
        {
          "when": {
            "work": [
              "BUSINESS",
              "PART_TIME"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "단시간 근로나 사업자를 미취업으로 보는 기준은 대학마다 달라요. 신청하려는 대학의 모집 공고를 확인해주세요."
        }
      ],
      "unknownExplanation": "지금 일하고 있는지, 근로시간과 고용보험 가입 여부를 확인해주세요."
    },
    {
      "questionId": "school",
      "label": "대학 재학",
      "evidence": "대학에 재학 중이지 않은 청년이 대상이에요. 공식 안내는 졸업 후 취업준비생, 1년 이상 장기 휴학생·졸업유예생을 추천 대상으로 들어요.",
      "cases": [
        {
          "when": {
            "school": [
              "NOT_ENROLLED"
            ]
          },
          "outcome": "MET",
          "explanation": "입력한 답변은 이 조건을 충족해요."
        },
        {
          "when": {
            "school": [
              "LONG_DEFERRAL",
              "LONG_LEAVE"
            ]
          },
          "outcome": "MET",
          "explanation": "공식 안내의 추천 대상이에요. 장기 휴학·졸업유예생을 받지 않는 대학도 있어 모집 공고를 확인해주세요."
        },
        {
          "when": {
            "school": [
              "ENROLLED"
            ]
          },
          "outcome": "NOT_MET",
          "explanation": "대학 재학생은 참여할 수 없어요."
        },
        {
          "when": {
            "school": [
              "GRADUATE_SCHOOL",
              "GRADUATING",
              "SHORT_LEAVE_OR_DEFERRAL"
            ]
          },
          "outcome": "UNKNOWN",
          "explanation": "1년 미만 휴학·졸업유예, 졸업예정자, 대학원생을 받는지는 대학마다 달라요. 신청하려는 대학의 모집 공고를 확인해주세요."
        }
      ],
      "unknownExplanation": "지금 학적(재학·휴학·졸업유예·졸업)을 확인해주세요."
    }
  ],
  "ageBinding": {
    "questionId": "age",
    "minimumInclusive": 19,
    "maximumInclusive": 34,
    "referenceDate": null,
    "below": "UNDER_19",
    "within": "AGE_19_TO_34",
    "above": "EXTENSION_PENDING",
    "showCalculatedAge": true
  },
  "ageNotice": "오늘(서울 기준) 신청 시 연령이에요. 연령 기준일은 공통 안내에 없어 대학별 모집 공고를 확인해주세요."
}
$rule$::jsonb, 'seed', '검토한 공고 규칙 초기 데이터', now(), 'seed');
INSERT INTO policy_rule_heads(policy_number, version_id) VALUES ('20260714005400113259', 'ba390000-0000-4000-8006-000000000001');

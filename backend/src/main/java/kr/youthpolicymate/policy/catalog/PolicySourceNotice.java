package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"title", "description", "sourceLabel", "sourceUrl"})
public record PolicySourceNotice(String title, String description, String sourceLabel, String sourceUrl) {
    static List<PolicySourceNotice> forContent(String number, String contentHash) {
        if ("20260722005400213264".equals(number) && "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea".equals(contentHash)) {
            return List.of(new PolicySourceNotice("5월 모집 신청 기간 확인",
                    "수집 안내는 5월 4~31일이지만, 공식 공고의 신청 기간은 2026년 5월 18~31일 23:59(서울)예요. 5월 4일은 공고 시작일이며 이후 2차 모집과 구분해주세요.",
                    "서울시 2026년 5월 모집 공고", "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004"),
                    new PolicySourceNotice("대학생 참여 예외 확인",
                            "수집 안내에는 대학생 참여 불가로 표시돼 있지만, 공식 공고에는 수료·졸업예정·졸업유예·방송통신·사이버·야간대학(원) 재학 예외가 있어요. 공고의 증빙 기준을 확인해주세요.",
                            "서울시 모집 공고와 첨부파일", "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004"),
                    new PolicySourceNotice("참고 링크 확인 필요",
                            "수집된 ‘추가 참고 안내’는 다른 정책으로 연결돼요. 아래 미래 청년 일자리 모집 공고를 확인해주세요.",
                            "미래 청년 일자리 모집 공고", "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004"));
        }
        if ("20260528005400113226".equals(number) && "10d0ad325b17feaafb3eec87cab65012a059ba1eab96de2f7ad1d8d8f164e18f".equals(contentHash)) {
            var announcement = "https://www.kawf.kr/notice/sub01View.do?selIdx=19285";
            return List.of(new PolicySourceNotice("2026년 모집 마감",
                    "수집 안내는 상시 접수로 표시하지만, 2026년 모집은 2월 4일 14시부터 13일 11시까지 선착순으로 받을 예정이었다가 첫날 예산이 소진돼 마감됐어요. 2026년 10월 11일 확인 기준 다음 모집 일정은 공지되지 않았어요.",
                    "한국예술인복지재단 신청접수 마감 안내(2026.2.4.)", "https://www.kawf.kr/notice/sub01View.do?selIdx=19288"),
                    new PolicySourceNotice("연령·소득 기준 확인",
                            "수집 안내에는 연령·소득 기준이 없어요. 2026년 공고는 신청일 기준 만 18~39세이고, 2024년 귀속 소득금액증명원의 개인 소득금액이 2026년 개인 중위소득 120%(연 36,925,027원) 이내인 예술인이 대상이었어요. 다음 모집 기준은 달라질 수 있어요.",
                            "한국예술인복지재단 2026년 사업 공고", announcement),
                    new PolicySourceNotice("참여 제한 구분",
                            "수집 안내의 ‘참여 제한’은 선정된 뒤 지원금을 주지 않는 조건이에요. 2026년 공고의 참여 제한은 2025년 선정자(중도 해지자 포함) 등이고, 2026년 선정자는 2026년 예술활동준비금을 신청할 수 없어요.",
                            "한국예술인복지재단 2026년 사업 공고", announcement));
        }
        if (!"20260430005400113009".equals(number) || !"f3709a60376cdaf861ee232c1fc411292f2d3bdcb28c807ca87190a19698733c".equals(contentHash)) return List.of();
        return List.of(new PolicySourceNotice(
                "소득 기준 확인 필요",
                "수집된 안내의 가구 소득인정액 기준이 중위소득 50% 이하와 100% 이하로 서로 달라요. "
                        + "복지로의 2026년 모집 공고는 50% 이하로 안내해요. 자세한 신청 기준은 해당 공고를 확인해주세요.",
                "복지로 2026년 모집 공고",
                "https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html"),
                new PolicySourceNotice("출생일 기준 확인 필요",
                        "수집 안내와 2026년 사업 지침의 출생일 기준이 달라요. 지침에 따른 5월 모집 대상은 1986.5.1.~2011.5.31. 출생자예요.",
                        "2026년 사업 지침 (61쪽)", "https://hope.welfareinfo.or.kr/pds/GuideLine.pdf"));
    }
}

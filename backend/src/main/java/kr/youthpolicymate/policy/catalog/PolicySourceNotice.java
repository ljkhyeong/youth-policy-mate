package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"title", "description", "sourceLabel", "sourceUrl"})
public record PolicySourceNotice(String title, String description, String sourceLabel, String sourceUrl) {
    static List<PolicySourceNotice> forContent(String number, String contentHash) {
        if (FutureYouthJobsRules.NUMBER.equals(number) && FutureYouthJobsRules.CONTENT_HASH.equals(contentHash)) {
            return List.of(new PolicySourceNotice("5월 모집 신청 기간 확인",
                    "수집 안내는 5월 4~31일이지만, 공식 공고의 신청 기간은 2026년 5월 18~31일 23:59(서울)예요. 5월 4일은 공고 시작일이며 이후 2차 모집과 구분해주세요.",
                    "서울시 2026년 5월 모집 공고", FutureYouthJobsRules.SOURCE),
                    new PolicySourceNotice("대학생 참여 예외 확인",
                            "수집 안내에는 대학생 참여 불가로 표시돼 있지만, 공식 공고에는 수료·졸업예정·졸업유예·방송통신·사이버·야간대학(원) 재학 예외가 있어요. 공고의 증빙 기준을 확인해주세요.",
                            "서울시 모집 공고와 첨부파일", FutureYouthJobsRules.SOURCE),
                    new PolicySourceNotice("참고 링크 확인 필요",
                            "수집된 ‘추가 참고 안내’는 다른 정책으로 연결돼요. 아래 미래 청년 일자리 모집 공고를 확인해주세요.",
                            "미래 청년 일자리 모집 공고", FutureYouthJobsRules.SOURCE));
        }
        if (!YouthTomorrowSavingsRules.NUMBER.equals(number) || !YouthTomorrowSavingsRules.CONTENT_HASH.equals(contentHash)) return List.of();
        return List.of(new PolicySourceNotice(
                "소득 기준 확인 필요",
                "수집된 안내의 가구 소득인정액 기준이 중위소득 50% 이하와 100% 이하로 서로 달라요. "
                        + "복지로의 2026년 모집 공고는 50% 이하로 안내해요. 자세한 신청 기준은 해당 공고를 확인해주세요.",
                "복지로 2026년 모집 공고",
                "https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html"),
                new PolicySourceNotice("출생일 기준 확인 필요",
                        "수집 안내와 2026년 사업 지침의 출생일 기준이 달라요. 지침에 따른 5월 모집 대상은 1986.5.1.~2011.5.31. 출생자예요.",
                        "2026년 사업 지침 (61쪽)", YouthTomorrowSavingsRules.SOURCE));
    }
}

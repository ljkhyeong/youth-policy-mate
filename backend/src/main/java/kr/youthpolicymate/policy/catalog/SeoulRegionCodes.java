package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.SeoulDistrict;

/**
 * 공개 목록 지역 필터가 쓰는 온통청년 공고 지역 코드(법정동코드 앞 5자리 시군구)다.
 * 코드는 공고 지역이지 거주 자격이 아니므로 자격 판정의 SeoulDistrict 값과 분리해 둔다.
 * 코드표는 행정안전부 법정동코드 기준이며 조사 기록은 docs/research/public-region-filter.md에 있다.
 */
final class SeoulRegionCodes {
    /** 서울특별시 전체 코드. 공고 지역에 이 코드만 있으면 자치구를 가를 수 없어 모든 자치구 필터에 포함한다. */
    static final String SEOUL = "11000";

    private SeoulRegionCodes() {}

    // switch에 default를 두지 않아 자치구가 추가되면 컴파일 단계에서 코드 누락이 드러난다.
    static String code(SeoulDistrict district) {
        return switch (district) {
            case JONGNO -> "11110"; case JUNG -> "11140"; case YONGSAN -> "11170"; case SEONGDONG -> "11200";
            case GWANGJIN -> "11215"; case DONGDAEMUN -> "11230"; case JUNGNANG -> "11260"; case SEONGBUK -> "11290";
            case GANGBUK -> "11305"; case DOBONG -> "11320"; case NOWON -> "11350"; case EUNPYEONG -> "11380";
            case SEODAEMUN -> "11410"; case MAPO -> "11440"; case YANGCHEON -> "11470"; case GANGSEO -> "11500";
            case GURO -> "11530"; case GEUMCHEON -> "11545"; case YEONGDEUNGPO -> "11560"; case DONGJAK -> "11590";
            case GWANAK -> "11620"; case SEOCHO -> "11650"; case GANGNAM -> "11680"; case SONGPA -> "11710";
            case GANGDONG -> "11740";
        };
    }
}

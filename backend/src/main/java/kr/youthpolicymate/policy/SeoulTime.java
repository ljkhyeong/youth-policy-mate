package kr.youthpolicymate.policy;

import java.time.ZoneId;

/** 마감·알림·수집 예산의 달력 날짜는 주입한 Clock의 시간대가 아니라 항상 서울 기준으로 계산한다. */
public final class SeoulTime {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private SeoulTime() {}
}

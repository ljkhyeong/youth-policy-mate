package kr.youthpolicymate.admin;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** 지정 관리자 한 명을 두고 알림·메일·수집 일정·AI 자동 실행을 끈 관리자 API 테스트 설정. */
@Target(TYPE)
@Retention(RUNTIME)
@SpringBootTest(properties = {"app.admin.member-ids=" + AdminTestSupport.ADMIN, "app.reminders.enabled=false",
        "app.email.enabled=false", "app.ontong.schedule.enabled=false", "app.ai.auto.enabled=false"})
@AutoConfigureMockMvc
public @interface AdminApiTest {}

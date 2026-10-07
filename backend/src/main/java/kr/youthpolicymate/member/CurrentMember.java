package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 회원 ID(OAuth2User 이름 = 회원 ID). `/api/v1/me/**`·관리자 API처럼 ROLE_MEMBER 인증과
 * MemberSessionFilter의 회원 확인을 거친 경로에서만 쓴다. 비로그인 허용 경로에서는 변환에 실패한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(name)")
@Parameter(hidden = true)
public @interface CurrentMember {}

package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "MemberEmailAddress", requiredProperties = {"address"})
public record MemberEmailAddress(@Email @NotBlank @Size(max = 254) String address) {
    @Override public String toString() { return "MemberEmailAddress[이메일 주소 비공개]"; }
}

package kr.youthpolicymate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YouthPolicyMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouthPolicyMateApplication.class, args);
    }

    /** 저장소 루트 .env를 읽는 로컬 운영 명령용 컨텍스트. 웹 서버를 띄우지 않아 정기 작업이 생성되지 않고 이메일 발송은 끈다. */
    public static ConfigurableApplicationContext startCommand() {
        return new SpringApplicationBuilder(YouthPolicyMateApplication.class)
                .profiles("local").web(WebApplicationType.NONE)
                .run("--spring.config.import=optional:file:.env[.properties]", "--app.email.enabled=false");
    }
}

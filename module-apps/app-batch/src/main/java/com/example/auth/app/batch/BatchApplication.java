package com.example.auth.app.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 배치·정리 잡 실행 앱(휴면 사전통지·전환, 보존 파기).
 *
 * <p>도메인·infra·common 모듈을 컴포넌트 스캔으로 조립한다(모든 모듈이 {@code com.example.auth} 루트를
 * 공유). 잡 로직은 도메인 서비스가 소유하고 이 앱은 선택·구동·조율만 한다. 실행할 잡은
 * {@code app.batch.jobs}(CSV)로 지정한다.
 */
@SpringBootApplication(scanBasePackages = "com.example.auth")
@EntityScan({
    "com.example.auth.domain.auth.entity",
    "com.example.auth.domain.generic.entity",
    "com.example.auth.domain.user.entity"
})
@EnableJpaRepositories({
    "com.example.auth.domain.auth.repository",
    "com.example.auth.domain.generic.repository",
    "com.example.auth.domain.user.repository"
})
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}

package com.my.bookduck.config.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder; // RestTemplateBuilder 사용 권장

@Configuration
public class AppConfig { // 클래스 이름은 자유롭게 지정 가능

    // RestTemplateBuilder를 주입받아 사용하는 것이 더 권장됨
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 필요하다면 여기서 RestTemplate 설정을 추가할 수 있습니다.
        // 예: 타임아웃 설정
        // builder.setConnectTimeout(Duration.ofSeconds(5));
        // builder.setReadTimeout(Duration.ofSeconds(5));

        return builder.build(); // 설정된 빌더로 RestTemplate 생성 및 반환
    }

    /* 또는 가장 기본적인 방법 (빌더 없이)
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    */
}
package com.my.bookduck.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {

        // HTTP 클라이언트 타임아웃 설정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 연결 타임아웃: 5초
                .responseTimeout(Duration.ofSeconds(15)) // 응답 타임아웃: 15초 (API 호출 시간 고려)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS)) // 읽기 타임아웃: 15초
                                .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.SECONDS))); // 쓰기 타임아웃: 15초

        // *** JSON 처리를 위한 ExchangeStrategies 설정 추가 ***
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    // 기본 코덱 설정 (Jackson JSON 코덱 포함)
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 버퍼 크기 증가 (예: 10MB) - 응답 클 경우 대비
                    // configurer.defaultCodecs().enableLoggingRequestDetails(true); // 요청/응답 로깅 활성화 (디버깅 시 유용)
                })
                .build();

        // 로깅 활성화 (선택적)
        // exchangeStrategies
        //         .messageWriters().stream()
        //         .filter(LoggingCodecSupport.class::isInstance)
        //         .forEach(writer -> ((LoggingCodecSupport)writer).setEnableLoggingRequestDetails(true));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies) // *** 설정된 ExchangeStrategies 적용 ***
                // .baseUrl("기본 URL 설정 가능")
                // .defaultHeader("Content-Type", "application/json")
                // .defaultCookie("cookie-name", "cookie-value")
                .build();
    }
}
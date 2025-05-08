package com.my.bookduck.controller;

import com.my.bookduck.service.AladinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/aladin")
@RequiredArgsConstructor
@Slf4j
public class AladinApiController {

    @Value("${aladin.api.key}")
    private String ttbKey;

    private final RestTemplate restTemplate;
    private final AladinService aladinService;

    @GetMapping("/search")
    public ResponseEntity<String> searchBooks(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        StringBuilder url = new StringBuilder("http://www.aladin.co.kr/ttb/api/ItemSearch.aspx?");
        url.append("ttbkey=").append(ttbKey);
        url.append("&Query=").append(query != null ? query : "");
        url.append("&QueryType=Keyword");
        url.append("&MaxResults=10");
        url.append("&start=1");
        url.append("&SearchTarget=Book");
        url.append("&output=js");
        url.append("&Version=20131101");
        if (categoryId != null) {
            url.append("&CategoryId=").append(categoryId);
        }

        String response = restTemplate.getForObject(url.toString(), String.class);
        return ResponseEntity.ok(response);
    }

    /**
     * 알라딘 일반 도서 종합 베스트셀러 다음 배치를 동기화합니다.
     */
    @PostMapping("/bestsellers/next-batch")
    public Mono<ResponseEntity<String>> syncNextBestsellerBatch() {
        log.info("알라딘 일반 도서 종합 베스트셀러 다음 배치 동기화 요청 수신");
        int categoryId = AladinService.DEFAULT_BESTSELLER_CATEGORY_ID;
        return aladinService.fetchNextBestsellerBatch(categoryId)
                .map(result -> {
                    String messageBody = String.format(
                            "알라딘 일반 도서 베스트셀러(CatId:%d) 배치(시작:%d, 시도:%d, 실제처리:%d) 동기화 완료. API 조회:%d, 신규:%d, 업데이트:%d",
                            categoryId, result.getStartPage(), result.getLastAttemptedPage(), result.getActualLastProcessedPage(),
                            result.getTotalApiItems(), result.getSavedCount(), result.getUpdatedCount()
                    );
                    if (result.isErrorOccurred()) {
                        log.error("일반 도서 베스트셀러(CatId:{}) 다음 배치 동기화 중 오류 발생. 상세: {}", categoryId, messageBody);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(messageBody + " (오류 발생)");
                    }
                    log.info(messageBody);
                    return ResponseEntity.ok(messageBody);
                })
                .onErrorResume(error -> {
                    log.error("알라딘 일반 도서 베스트셀러 다음 배치 동기화 최종 에러", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("일반 도서 동기화 처리 중 예상치 못한 오류 발생: " + error.getMessage()));
                });
    }

    // ★추가★: eBook 베스트셀러 동기화를 위한 새로운 엔드포인트
    /**
     * 알라딘 eBook 종합 베스트셀러 다음 배치를 동기화합니다.
     */
    @PostMapping("/ebook-bestsellers/next-batch")
    public Mono<ResponseEntity<String>> syncNextEBookBestsellerBatch() {
        log.info("알라딘 eBook 종합 베스트셀러 다음 배치 동기화 요청 수신");
        // eBook 종합 베스트셀러의 카테고리 ID 사용 (AladinService에 정의된 상수 사용)
        int categoryId = AladinService.DEFAULT_EBOOK_BESTSELLER_CATEGORY_ID;
        return aladinService.fetchNextEBookBestsellerBatch(categoryId) // 서비스의 eBook용 메소드 호출
                .map(result -> {
                    String messageBody = String.format(
                            "알라딘 eBook 베스트셀러(CatId:%d) 배치(시작:%d, 시도:%d, 실제처리:%d) 동기화 완료. API 조회:%d, 신규:%d, 업데이트:%d",
                            categoryId, result.getStartPage(), result.getLastAttemptedPage(), result.getActualLastProcessedPage(),
                            result.getTotalApiItems(), result.getSavedCount(), result.getUpdatedCount()
                    );
                    if (result.isErrorOccurred()) {
                        log.error("eBook 베스트셀러(CatId:{}) 다음 배치 동기화 중 오류 발생. 상세: {}", categoryId, messageBody);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(messageBody + " (오류 발생)");
                    }
                    log.info(messageBody);
                    return ResponseEntity.ok(messageBody);
                })
                .onErrorResume(error -> {
                    log.error("알라딘 eBook 베스트셀러 다음 배치 동기화 최종 에러", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("eBook 동기화 처리 중 예상치 못한 오류 발생: " + error.getMessage()));
                });
    }

}
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

    @PostMapping("/bestsellers/next-batch") // 경로 변경 또는 파라미터 추가 고려
    public Mono<ResponseEntity<String>> syncNextBestsellerBatch() {
        log.info("알라딘 종합 베스트셀러 다음 배치 동기화 요청 수신");
        // 카테고리 ID는 예시로 0 사용
        int categoryId = AladinService.DEFAULT_BESTSELLER_CATEGORY_ID;
        return aladinService.fetchNextBestsellerBatch(categoryId) // 새로 만든 메소드 호출
                .map(result -> {
                    if (result.isErrorOccurred()) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(String.format("베스트셀러(CatId:%d) 다음 배치 동기화 중 오류 발생.", categoryId));
                    }
                    String message = String.format(
                            "알라딘 베스트셀러(CatId:%d) 배치(시작:%d, 시도:%d, 실제처리:%d) 동기화 완료. API 조회:%d, 신규:%d, 업데이트:%d",
                            categoryId, result.getStartPage(), result.getLastAttemptedPage(), result.getActualLastProcessedPage(),
                            result.getTotalApiItems(), result.getSavedCount(), result.getUpdatedCount()
                    );
                    log.info(message);
                    return ResponseEntity.ok(message);
                })
                .onErrorResume(error -> { // 최종 에러 처리
                    log.error("알라딘 베스트셀러 다음 배치 동기화 최종 에러", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("동기화 처리 중 예상치 못한 오류 발생: " + error.getMessage()));
                });
    }

}
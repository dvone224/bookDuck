package com.my.bookduck.service;

import com.my.bookduck.controller.response.AladinApiResponse;
import com.my.bookduck.controller.response.AladinBookItem;
import com.my.bookduck.controller.response.PaginatedAladinResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
public class AladinService {

    // application.properties에서 값 주입
    @Value("${aladin.api.key}")
    private String apiKey;

    @Value("${aladin.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    // RestTemplate 주입 (Configuration에서 Bean으로 등록하거나 직접 생성)
    public AladinService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PaginatedAladinResponse searchBooks(String query, int page, int size) {
        if (query == null || query.trim().isEmpty()) {
            // 빈 결과 반환
            return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
        }

        // API 요청 URL 생성 (start와 MaxResults 파라미터 사용)
        URI uri = UriComponentsBuilder
                .fromUriString(apiUrl)
                .queryParam("TTBKey", apiKey)
                .queryParam("Query", query)
                .queryParam("QueryType", "Keyword")
                .queryParam("MaxResults", size)       // 페이지당 항목 수
                .queryParam("start", page)           // 요청할 페이지 번호 (알라딘은 1부터 시작)
                .queryParam("SearchTarget", "eBook") // eBook 검색 유지
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        try {
            // API 호출 및 응답 받기 (AladinApiResponse는 전체 응답 구조 DTO)
            AladinApiResponse response = restTemplate.getForObject(uri, AladinApiResponse.class);

            if (response != null) {
                List<AladinBookItem> books = (response.getItem() != null) ? response.getItem() : Collections.emptyList();
                // 페이징된 결과 객체 생성하여 반환
                return new PaginatedAladinResponse(books, response.getTotalResults(), page, size);
            } else {
                System.out.println("알라딘 API 응답이 null입니다.");
                return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
            }
        } catch (Exception e) {
            System.err.println("알라딘 API 호출 중 오류 발생: " + e.getMessage());
            return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size); // 오류 시 빈 결과 반환
        }
    }
}
package com.my.bookduck.controller.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data // Lombok (Getter, Setter 등 자동 생성)
@JsonIgnoreProperties(ignoreUnknown = true) // 모르는 필드는 무시
public class AladinApiResponse {
    private String version;
    private String title;
    private String link;
    private int totalResults;
    private int startIndex;
    private int itemsPerPage;
    private String query;
    private List<AladinBookItem> item; // 책 목록
    // 기타 필요한 필드...

    // 중요: 책 아이템 리스트. JSON 응답의 키 이름이 'item' 또는 'book' 등일 수 있습니다.
    // 실제 응답의 키 이름과 Java 필드명이 다르면 @JsonProperty 로 매핑해줍니다.
//    @JsonProperty("item") // 예시: JSON 키가 "item"일 경우
//    private List<AladinBookItem> items; // 개별 책 정보를 담는 리스트
}


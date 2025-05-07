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
    private Integer totalResults; // 전체 결과 수 (null 가능하도록 Integer 사용)
    private Integer startIndex;     // 현재 페이지 시작 인덱스 (null 가능하도록 Integer 사용)
    private Integer itemsPerPage;   // 페이지 당 결과 수 (null 가능하도록 Integer 사용)
    private String query;           // 검색어
    private List<AladinBookItem> item; // 책 아이템 목록

}


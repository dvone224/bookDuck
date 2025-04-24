package com.my.bookduck.controller.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
}


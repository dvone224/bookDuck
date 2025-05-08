package com.my.bookduck.controller.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AladinBookItem {
    private String title;
    private String author;
    private String publisher;
    private String pubDate; // 출간일
    private String cover; // 표지 이미지 URL
    private Long isbn13; // ISBN
    private int priceStandard; // 정가
    private String description;
    private String link; // 알라딘 상품 링크
    private int categoryId;
    private String mallType;
    // 기타 필요한 필드...
}

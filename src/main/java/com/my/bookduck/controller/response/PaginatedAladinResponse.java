package com.my.bookduck.controller.response;

// 예: PaginatedAladinResponse.java
import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedAladinResponse {
    private List<AladinBookItem> books;   // 현재 페이지의 책 목록
    private int totalResults;          // 전체 결과 수
    private int currentPage;           // 현재 페이지 번호
    private int itemsPerPage;          // 페이지당 항목 수
    // 필요하다면 totalPages 도 계산해서 포함 가능
    // private int totalPages;
}
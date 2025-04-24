package com.my.bookduck.controller;

import com.my.bookduck.controller.response.AladinBookItem;
import com.my.bookduck.controller.response.PaginatedAladinResponse;
import com.my.bookduck.service.AladinService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class BookSearchController {
    private final AladinService aladinService;

    public BookSearchController(AladinService aladinService) {
        this.aladinService = aladinService;
    }

    @GetMapping("/books")
    public PaginatedAladinResponse searchBooks(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,  // 기본값 1페이지
            @RequestParam(defaultValue = "10") int size) { // 기본값 페이지당 10개
        // size 값 제한 (예: 최대 50) - 알라딘 API 제한 고려
        int effectiveSize = Math.min(Math.max(size, 1), 50); // 최소 1, 최대 50으로 제한
        return aladinService.searchBooks(query, page, effectiveSize);
    }
}
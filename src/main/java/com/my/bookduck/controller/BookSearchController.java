package com.my.bookduck.controller;

import com.my.bookduck.controller.response.AladinBookItem;
import com.my.bookduck.controller.response.PaginatedAladinResponse;
import com.my.bookduck.service.AladinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@Slf4j
public class BookSearchController {
    private final AladinService aladinService;

    public BookSearchController(AladinService aladinService) {
        this.aladinService = aladinService;
    }

    @GetMapping("/books")
    public PaginatedAladinResponse searchOrListBooks(
            @RequestParam(required = false, defaultValue = "") String query, // 검색어는 선택 사항
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false, defaultValue = "PublishTime") String sort) {

        log.info("[Controller] /api/search/books 호출 - query: '{}', page: {}, size: {}, categoryId: '{}', sort: '{}'",
                query, page, size, categoryId, sort);

        // 입력값 유효성 검사 및 기본값 설정 (서비스 레벨에서도 처리하지만 컨트롤러에서도 간단히)
        int effectiveSize = Math.min(Math.max(size, 1), 50); // 알라딘 API 최대 50개 제한 고려

        // 서비스 호출 시 모든 파라미터 전달
         return aladinService.searchBooks(query, page, effectiveSize, categoryId, sort);
    }

//    @GetMapping("/bestsellers")
//    public PaginatedAladinResponse getBestsellers(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(required = false) String categoryId,
//            @RequestParam(required = false, defaultValue = "PublishTime") String sort) {
//        int effectiveSize = Math.min(Math.max(size, 1), 50);
//        String effectiveSort = sort != null && List.of("PublishTime", "SalesPoint", "Title").contains(sort)
//                ? sort
//                : "PublishTime";
//        String effectiveCategoryId = (categoryId != null && !categoryId.trim().isEmpty())
//                ? categoryId
//                : null;
//        return aladinService.getBestsellers(page, effectiveSize, effectiveCategoryId, effectiveSort);
//    }
}
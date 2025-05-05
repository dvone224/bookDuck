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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false, defaultValue = "PublishTime") String sort) {
        int effectiveSize = Math.min(Math.max(size, 1), 50);
        String effectiveSort = sort != null && List.of("PublishTime", "SalesPoint", "Title").contains(sort)
                ? sort
                : "PublishTime";
        String effectiveCategoryId = (categoryId != null && !categoryId.trim().isEmpty())
                ? categoryId
                : null;
        return aladinService.searchBooks(query, page, effectiveSize, effectiveCategoryId, effectiveSort);
    }

    @GetMapping("/bestsellers")
    public PaginatedAladinResponse getBestsellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false, defaultValue = "PublishTime") String sort) {
        int effectiveSize = Math.min(Math.max(size, 1), 50);
        String effectiveSort = sort != null && List.of("PublishTime", "SalesPoint", "Title").contains(sort)
                ? sort
                : "PublishTime";
        String effectiveCategoryId = (categoryId != null && !categoryId.trim().isEmpty())
                ? categoryId
                : null;
        return aladinService.getBestsellers(page, effectiveSize, effectiveCategoryId, effectiveSort);
    }
}
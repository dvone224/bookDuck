package com.my.bookduck.controller;

import com.my.bookduck.controller.response.CategorySimpleDto; // DTO 임포트
import com.my.bookduck.domain.book.Category;
import com.my.bookduck.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors; // Stream API 사용

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<CategorySimpleDto>> getSubCategories(@PathVariable Long parentId) {
        List<Category> subCategoriesEntities = categoryService.getSubCategories(parentId);
        log.info("parentId = " + parentId);
        // 엔티티 리스트를 DTO 리스트로 변환
        List<CategorySimpleDto> subCategoriesDto = subCategoriesEntities.stream()
                .map(CategorySimpleDto::new) // 생성자 참조 사용
                .collect(Collectors.toList());

        return ResponseEntity.ok(subCategoriesDto); // DTO 리스트 반환
    }
}
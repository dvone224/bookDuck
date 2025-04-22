package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final CategoryService categoryService; // CategoryService 주입

    // ... (createBook 등 다른 메소드) ...

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    /**
     * 검색어와 선택된 카테고리(대분류 또는 소분류) 기준으로 책을 검색합니다.
     * @param query 검색어 (제목 또는 저자)
     * @param mainCategoryId 선택된 대분류 ID (없으면 null)
     * @param subCategoryId 선택된 소분류 ID (없으면 null)
     * @return 필터링 및 검색된 책 리스트
     */
    public List<Book> searchBooks(String query, Long mainCategoryId, Long subCategoryId) {
        String trimmedQuery = (query != null) ? query.trim() : null; // 비어있으면 null로 처리
        Set<Long> targetCategoryIds = null; // 필터링할 최종 카테고리 ID 목록

        log.info("Searching books - Query: '{}', MainCategory: {}, SubCategory: {}",
                trimmedQuery, mainCategoryId, subCategoryId);

        if (subCategoryId != null) {
            // 1. 소분류가 명확히 선택된 경우: 해당 소분류 ID만 사용
            targetCategoryIds = Set.of(subCategoryId);
            log.debug("Filtering by specific sub-category ID: {}", subCategoryId);
        } else if (mainCategoryId != null) {
            // 2. 대분류만 선택된 경우: 대분류 ID + 모든 하위 소분류 ID 사용
            targetCategoryIds = categoryService.getAllSubCategoryIdsIncludingMain(mainCategoryId);
            log.debug("Filtering by main category ID {} and its sub-categories: {}", mainCategoryId, targetCategoryIds);
            if (targetCategoryIds.isEmpty()) {
                // 대분류 ID는 있는데 하위 카테고리가 없는 이상한 경우? 안전하게 빈 리스트 반환
                log.warn("Main category {} selected, but no corresponding category IDs found.", mainCategoryId);
                return Collections.emptyList();
            }
        } else {
            // 3. 카테고리 선택이 없는 경우: targetCategoryIds는 null 유지 (필터링 안 함)
            log.debug("No category filter applied.");
        }

        // Repository 호출: 검색어(null 가능)와 카테고리 ID 목록(null 가능) 전달
        return bookRepository.findBooksByQueryAndCategoryIdsIn(trimmedQuery, targetCategoryIds);
    }
}
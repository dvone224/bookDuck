package com.my.bookduck.controller;

import com.my.bookduck.controller.response.BookLIstViewResponse; // DTO 클래스 확인
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.Category;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CategoryService;
// import com.my.bookduck.service.EBookService; // 필요하면 주석 해제
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors; // Collectors 임포트

@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    // private final EBookService eBookService; // 필요하면 주석 해제
    private final CategoryService categoryService;

    /**
     * 도서 목록 페이지 초기 로딩 및 검색/필터링 결과 표시 (전체 페이지 렌더링)
     */
    @GetMapping("/books")
    public String listOrSearchBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam, // JS에서 대분류만 선택 시 전달
            @RequestParam(value = "categoryId", required = false) Long categoryId,         // JS에서 소분류 선택 시 전달 (이름 통일)
            Model model) {

        log.info("====== Full Page Load Request ======");
        log.info("GET /books - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        // 최종 필터링에 사용할 ID 결정 (BookService의 로직과 일치시킴)
        Long finalMainCategoryId = null;
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            // 1. 소분류 ID가 명확히 전달된 경우 (JS에서 소분류 선택)
            finalSubCategoryId = categoryId;
            // 해당 소분류의 부모(대분류)를 찾아 화면 표시에 사용
            Optional<Category> subCatOpt = categoryService.findById(categoryId);
            if (subCatOpt.isPresent() && subCatOpt.get().getParent() != null) {
                finalMainCategoryId = subCatOpt.get().getParent().getId();
            } else {
                // 소분류는 있는데 부모가 없거나 못찾는 경우? 일단 mainCategoryIdParam 값을 사용하거나 null 유지
                finalMainCategoryId = mainCategoryIdParam;
                log.warn("SubCategory {} found, but parent category could not be determined or mainCategoryIdParam ({}) provided.", categoryId, mainCategoryIdParam);
            }
            log.debug("Filtering by SubCategory ID: {}, Determined MainCategory ID for display: {}", finalSubCategoryId, finalMainCategoryId);

        } else if (mainCategoryIdParam != null) {
            // 2. 소분류 ID는 없고 대분류 ID만 전달된 경우 (JS에서 대분류만 선택)
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
            log.debug("Filtering by MainCategory ID: {}", finalMainCategoryId);
        } else {
            log.debug("No category filter applied.");
        }

        // --- 서비스 호출 ---
        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        // --- 페이지 제목 및 메시지 설정 (기존 로직 유지) ---
        String pageTitle;
        String message = null;
        String trimmedQuery = query.trim();
        Category titleCategory = null; // 제목 표시에 사용할 카테고리

        // 제목 생성을 위해 최종 결정된 카테고리 ID 사용
        if (finalSubCategoryId != null) {
            titleCategory = categoryService.findById(finalSubCategoryId).orElse(null);
        } else if (finalMainCategoryId != null) {
            titleCategory = categoryService.findById(finalMainCategoryId).orElse(null);
        }

        // (페이지 제목 및 메시지 설정 로직은 제공된 코드 그대로 유지)
        if (!trimmedQuery.isEmpty() || titleCategory != null) {
            StringBuilder titleBuilder = new StringBuilder();
            if (titleCategory != null) {
                titleBuilder.append("[").append(titleCategory.getName()).append("] ");
            }
            if (!trimmedQuery.isEmpty()) {
                titleBuilder.append("'").append(trimmedQuery).append("' ");
            }
            titleBuilder.append("검색 결과");
            pageTitle = titleBuilder.toString();
            if (booksResult.isEmpty()) {
                message = "조건에 맞는 검색 결과가 없습니다.";
            }
        } else {
            pageTitle = "전체 도서 목록";
            if (booksResult.isEmpty()) {
                message = "등록된 도서가 없습니다.";
            }
        }


        // --- DTO 변환 ---
        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList()); // .toList() 대신 .collect(Collectors.toList()) 사용 (호환성)

        // --- 모델에 전체 페이지 렌더링을 위한 모든 정보 추가 ---
        List<Category> mainCategories = categoryService.getMainCategories();
        List<Category> subCategories = List.of(); // 기본 빈 리스트
        if (finalMainCategoryId != null) {
            // 현재 선택된 대분류에 맞는 소분류 목록 조회
            subCategories = categoryService.getSubCategories(finalMainCategoryId);
        }

        model.addAttribute("list", responseList);           // 도서 목록 DTO
        model.addAttribute("searchQuery", query);          // 현재 검색어
        if (message != null) {
            model.addAttribute("message", message);        // 결과 메시지
        }
        model.addAttribute("pageTitle", pageTitle);        // 페이지 제목
        model.addAttribute("mainCategories", mainCategories); // 전체 대분류 목록 (사이드바용)
        model.addAttribute("subCategories", subCategories);   // 현재 선택된 대분류의 소분류 목록 (사이드바용)
        model.addAttribute("selectedMainCategoryId", finalMainCategoryId); // 최종 선택된 대분류 ID (사이드바 selected 유지용)
        model.addAttribute("selectedSubCategoryId", finalSubCategoryId);   // 최종 선택된 소분류 ID (사이드바 selected 유지용)

        log.debug("Model attributes for full page - selectedMainCategoryId: {}, selectedSubCategoryId: {}",
                model.getAttribute("selectedMainCategoryId"), model.getAttribute("selectedSubCategoryId"));

        // 템플릿 경로 확인 (booklist.html 이 맞는지 확인)
        return "book/booklist";
    }


    /**
     * AJAX 요청을 처리하여 필터링된 도서 목록의 HTML 조각(tbody)을 반환하는 메소드
     */
    @GetMapping("/books/filter")
    public String filterBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam, // JS에서 대분류만 선택 시 전달
            @RequestParam(value = "categoryId", required = false) Long categoryId,         // JS에서 소분류 선택 시 전달
            Model model) {

        log.info("====== AJAX Filter Request ======");
        log.info("GET /books/filter - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        // 최종 필터링에 사용할 ID 결정 (listOrSearchBooks와 동일한 로직 적용)
        Long finalMainCategoryId = null; // AJAX 응답 자체에는 필요 없지만, 서비스 호출에는 필요
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            finalSubCategoryId = categoryId;
            // 부모 ID 찾기 (서비스 호출 시 필요할 수 있으나, 여기선 로깅/디버깅 용도로만 사용)
            Optional<Category> subCatOpt = categoryService.findById(categoryId);
            if (subCatOpt.isPresent() && subCatOpt.get().getParent() != null) {
                finalMainCategoryId = subCatOpt.get().getParent().getId();
            } else {
                finalMainCategoryId = mainCategoryIdParam;
            }
            log.debug("AJAX: Filtering by SubCategory ID: {}", finalSubCategoryId);
        } else if (mainCategoryIdParam != null) {
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
            log.debug("AJAX: Filtering by MainCategory ID: {}", finalMainCategoryId);
        } else {
            log.debug("AJAX: No category filter applied.");
        }


        // --- 서비스 호출 ---
        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        // --- DTO 변환 ---
        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList());

        // --- 모델에는 HTML 조각 렌더링에 필요한 목록만 추가 ---
        model.addAttribute("list", responseList);
        // 메시지는 tbody 안에 직접 포함되도록 HTML 조각에서 처리 (아래 반환 경로 참고)

        // --- Thymeleaf Fragment 반환 ---
        // "book/booklist" 템플릿 파일 내에서 id가 "bookTableBody"인 요소의 *내용*만 렌더링하여 반환
        log.info("Returning HTML fragment for #bookTableBody with {} books.", responseList.size());
        return "book/booklist :: #bookTableBody"; // 경로는 실제 파일 위치에 맞게 조정
    }

    // ... 기타 필요한 API 엔드포인트 (예: 소분류 목록 조회 API) ...
    // 예시:
    @GetMapping("/api/categories/{parentId}/subcategories")
    @ResponseBody // JSON 반환 명시
    public List<Category> getSubCategoriesApi(@PathVariable Long parentId) {
        log.debug("API request for subcategories of parentId: {}", parentId);
        // CategoryService를 사용하여 소분류 목록 조회 (Category 엔티티 직접 반환 또는 DTO 변환)
        return categoryService.getSubCategories(parentId);
    }

}
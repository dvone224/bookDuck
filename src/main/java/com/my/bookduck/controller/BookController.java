package com.my.bookduck.controller;

// ... (기존 import 유지) ...

import com.my.bookduck.controller.response.BookLIstViewResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.Category;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CategoryService;
import com.my.bookduck.service.EBookService; // 필요시 유지
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final EBookService eBookService; // 필요시 유지
    private final CategoryService categoryService;

    @GetMapping("/books")
    public String listOrSearchBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryId", required = false) Long mainCategoryIdParam, // 사이드바에서 넘어올 수 있음
            @RequestParam(value = "categoryId", required = false) String categoryIdStr, // 소분류 또는 대분류 ID일 수 있음
            Model model) {

        Long finalMainCategoryId = mainCategoryIdParam; // 최종 대분류 필터 ID
        Long finalSubCategoryId = null; // 최종 소분류 필터 ID

        // categoryIdStr 파싱 및 소분류/대분류 판별
        if (categoryIdStr != null && !categoryIdStr.trim().isEmpty()) {
            try {
                Long parsedCategoryId = Long.parseLong(categoryIdStr.trim());
                Optional<Category> categoryOpt = categoryService.findById(parsedCategoryId);
                if (categoryOpt.isPresent()) {
                    Category selectedCategory = categoryOpt.get();
                    if (selectedCategory.getParent() == null) {
                        // categoryIdStr 값이 대분류 ID였음
                        finalMainCategoryId = parsedCategoryId;
                        finalSubCategoryId = null; // 소분류는 선택 안됨
                    } else {
                        // categoryIdStr 값이 소분류 ID였음
                        finalSubCategoryId = parsedCategoryId;
                        finalMainCategoryId = selectedCategory.getParent().getId(); // 부모(대분류) ID도 설정
                    }
                } else {
                    log.warn("Category ID '{}' not found, ignoring category filter.", categoryIdStr);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid categoryId parameter received: {}", categoryIdStr);
            }
        } else if (mainCategoryIdParam != null) {
            // categoryIdStr는 비어있고 mainCategoryIdParam만 넘어온 경우 (대분류만 선택)
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
        }


        // --- 서비스 호출 (대분류/소분류 ID 전달) ---
        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        // --- 페이지 제목 및 메시지 설정 ---
        String pageTitle;
        String message = null;
        String trimmedQuery = query.trim();
        Category titleCategory = null; // 제목 표시에 사용할 카테고리

        if (finalSubCategoryId != null) {
            titleCategory = categoryService.findById(finalSubCategoryId).orElse(null);
        } else if (finalMainCategoryId != null) {
            titleCategory = categoryService.findById(finalMainCategoryId).orElse(null);
        }

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
                .toList();

        // --- 모델에 카테고리 정보 추가 (뷰 렌더링용) ---
        List<Category> mainCategories = categoryService.getMainCategories();
        List<Category> subCategories = List.of();
        if (finalMainCategoryId != null) {
            subCategories = categoryService.getSubCategories(finalMainCategoryId);
        }

        model.addAttribute("list", responseList);
        model.addAttribute("searchQuery", query);
        if (message != null) {
            model.addAttribute("message", message);
        }
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("mainCategories", mainCategories);
        model.addAttribute("subCategories", subCategories); // 현재 선택된 대분류 하위 또는 빈 리스트
        model.addAttribute("selectedMainCategoryId", finalMainCategoryId); // 최종 선택된 대분류 ID
        model.addAttribute("selectedSubCategoryId", finalSubCategoryId);   // 최종 선택된 소분류 ID

        return "book/booklist";
    }


    // 1. 뷰어 HTML 페이지를 반환하는 메소드
    @GetMapping("/read/{id}")
    public String ebookReaderPage(@PathVariable Long id, Model model) {
        // Book book = bookService.getBookById(id);
        // if (book == null) { return "error/404"; /* 404 페이지 반환 */ }
        // model.addAttribute("bookTitle", book.getTitle());

        model.addAttribute("bookId", id); // JavaScript에서 사용할 수 있도록 ID 전달
        return "book/reader"; // src/main/resources/templates/reader.html 파일을 렌더링하여 반환
    }

    @CrossOrigin
    @GetMapping("/api/books/epub/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveEpub(@PathVariable Long id) {
        Path filePath = eBookService.getBookPath(id); // 서비스에서 Path 객체 가져오기
        if (filePath == null) {
            System.err.println("File path not found for id: " + id);
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/epub+zip"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                System.err.println("Could not read file: " + filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            System.err.println("Error creating URL for file path: " + filePath + " - " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            System.err.println("Error accessing file: " + filePath + " - " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    // ... (CategoryApiController 및 다른 BookController 메소드 유지) ...
}



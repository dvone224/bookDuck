package com.my.bookduck.controller;

import com.my.bookduck.controller.response.BookLIstViewResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.Category;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CategoryService;
import com.my.bookduck.service.EBookService;
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
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final EBookService eBookService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    @GetMapping("/books")
    public String listOrSearchBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        log.info("====== Full Page Load Request ======");
        log.info("GET /books - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        Long finalMainCategoryId = null;
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            finalSubCategoryId = categoryId;
            Optional<Category> subCatOpt = categoryService.findById(categoryId);
            if (subCatOpt.isPresent() && subCatOpt.get().getParent() != null) {
                finalMainCategoryId = subCatOpt.get().getParent().getId();
            } else {
                finalMainCategoryId = mainCategoryIdParam;
                log.warn("SubCategory {} found, but parent category could not be determined or mainCategoryIdParam ({}) provided.", categoryId, mainCategoryIdParam);
            }
            log.debug("Filtering by SubCategory ID: {}, Determined MainCategory ID for display: {}", finalSubCategoryId, finalMainCategoryId);
        } else if (mainCategoryIdParam != null) {
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
            log.debug("Filtering by MainCategory ID: {}", finalMainCategoryId);
        } else {
            log.debug("No category filter applied.");
        }

        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        String pageTitle;
        String message = null;
        String trimmedQuery = query.trim();
        Category titleCategory = null;

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

        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList());

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
        model.addAttribute("subCategories", subCategories);
        model.addAttribute("selectedMainCategoryId", finalMainCategoryId);
        model.addAttribute("selectedSubCategoryId", finalSubCategoryId);

        log.debug("Model attributes for full page - selectedMainCategoryId: {}, selectedSubCategoryId: {}", model.getAttribute("selectedMainCategoryId"), model.getAttribute("selectedSubCategoryId"));

        return "book/booklist";
    }

    @GetMapping("/read/{id}")
    public String ebookReaderPage(@PathVariable Long id, Model model) {
        model.addAttribute("bookId", id);
        return "book/reader";
    }

    @CrossOrigin
    @GetMapping("/api/books/epub/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveEpub(@PathVariable Long id) {
        Path filePath = eBookService.getBookPath(id);
        if (filePath == null) {
            log.error("File path not found for id: {}", id);
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/epub+zip"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                log.error("Could not read file: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("Error creating URL for file path: {} - {}", filePath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Error accessing file: {} - {}", filePath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/books/filter")
    public String filterBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        log.info("====== AJAX Filter Request ======");
        log.info("GET /books/filter - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        Long finalMainCategoryId = null;
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            finalSubCategoryId = categoryId;
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

        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList());

        model.addAttribute("list", responseList);
        log.info("Returning HTML fragment for #bookTableBody with {} books.", responseList.size());
        return "book/booklist :: #bookTableBody";
    }

    @GetMapping("/api/categories/{parentId}/subcategories")
    @ResponseBody
    public List<Category> getSubCategoriesApi(@PathVariable Long parentId) {
        log.debug("API request for subcategories of parentId: {}", parentId);
        return categoryService.getSubCategories(parentId);
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<BookDTO>> searchBooks(
            @RequestParam String title,
            @RequestParam(defaultValue = "8") int limit,
            Principal principal) {
        if (principal == null) {
            log.warn("No authenticated user found for book search");
            return ResponseEntity.status(401).body(List.of());
        }

        String loginId = principal.getName();
        log.debug("Searching books for user with loginId: {}", loginId);

        User user = userRepository.findByLoginId(loginId);
        if (user == null) {
            log.error("User not found for loginId: {}", loginId);
            return ResponseEntity.status(404).body(List.of());
        }

        Long userId = user.getId();
        List<Book> books = bookService.searchBooksByTitleAndUser(title, userId, limit);
        List<BookDTO> bookDTOs = books.stream()
                .map(book -> new BookDTO(book.getId(), book.getTitle()))
                .toList();
        log.debug("Found {} books for userId: {}", bookDTOs.size(), userId);
        return ResponseEntity.ok(bookDTOs);
    }

    private record BookDTO(Long id, String title) {}
}
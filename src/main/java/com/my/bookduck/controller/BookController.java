package com.my.bookduck.controller;

import com.my.bookduck.controller.request.AddBookRequest;
import com.my.bookduck.controller.response.BookLIstViewResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.service.BookInfoService;
import com.my.bookduck.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookInfoService bookInfoService;

    @PostMapping({"/",""})
    public String addBook(final @RequestBody AddBookRequest book, Model model) {
        log.info("request book ={}" , book);

        try {
            bookService.createBook(book);
        } catch (Exception e) {
            log.error("errMSG={}", e.getMessage());
            model.addAttribute("msg", e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/books")
    @ResponseBody
    public String getAllBooks(final Model model) {
        List<BookLIstViewResponse> books = bookService.getAllBooks().stream()
                .map(m -> new BookLIstViewResponse(m)).toList();

        if(books.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("list", books);
        return "list";
    }

    @GetMapping("/search")
    @ResponseBody
    public String searchList(@RequestParam(value = "query", required = false, defaultValue = "") String query, Model model) {
        log.info("Searching for books with query: {}", query);

        List<BookLIstViewResponse> responseList;

        // 검색어가 비어있지 않은 경우에만 검색 실행
        if (query != null && !query.trim().isEmpty()) {
            // BookService에 검색 기능 메서드 호출 (이 메서드는 Service 계층에 구현해야 함)
            // 예시: 제목 또는 저자에 검색어가 포함된 책 검색
            List<Book> foundBooks = bookService.searchBooks(query); // BookService에 이 메서드 구현 필요!

            responseList = foundBooks.stream()
                    .map(BookLIstViewResponse::new)
                    .toList();
            log.info("Found {} books for query '{}'", responseList.size(), query);
            model.addAttribute("message", responseList.isEmpty() ? "'" + query + "'에 대한 검색 결과가 없습니다." : "");
        } else {
            // 검색어가 없는 경우 빈 리스트 반환 또는 전체 목록 반환 등 정책 결정 필요
            log.info("Search query is empty. Returning empty list.");
            responseList = Collections.emptyList(); // 빈 리스트 반환
            model.addAttribute("message", "검색어를 입력해주세요.");
        }
        log.info("Returning list of {} books", responseList);
        model.addAttribute("list", responseList); // 검색 결과를 모델에 추가
        model.addAttribute("searchQuery", query); // 검색어를 모델에 추가하여 뷰에서 다시 보여줄 수 있음
        model.addAttribute("pageTitle", "도서 검색 결과"); // 뷰에 페이지 제목 전달
        return "list"; // 검색 결과도 동일한 'list' 뷰를 사용하여 표시 (뷰 템플릿 재사용)
    }


    @GetMapping("/read/{book_id}")
    @ResponseBody
    public String readBook(@PathVariable Long book_id, Model model) {
        return bookInfoService.getBookBody(book_id);
    }

}

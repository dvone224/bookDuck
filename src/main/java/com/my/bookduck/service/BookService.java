package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddBookRequest;
import com.my.bookduck.controller.response.BookLIstViewResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;

    @Transactional
    public void createBook(AddBookRequest bookDto)throws IllegalStateException {
        log.info("bookDto: {}", bookDto);
        Book book = bookDto.toEntity(bookDto);
        bookRepository.save(book);
    }

    public List<Book> getAllBooks() {
      //  log.info("getAllBooks");
        List<Book> books = bookRepository.findAll();
        return books;
    }

    public List<Book> searchBooks(String query) {
        log.info("Searching books with query: '{}'", query);

        // 검색어가 비어있거나 null인 경우 빈 리스트 반환
        if (query == null || query.trim().isEmpty()) {
            log.warn("Search query is empty or null. Returning empty list.");
            return Collections.emptyList(); // 빈 리스트 반환
        }

        // BookRepository에 검색 메서드 호출 (Repository에 해당 메서드 정의 필요)
        // 예: 제목 또는 저자에 query가 포함된 책 검색 (대소문자 무시)
        // 이 메서드는 BookRepository 인터페이스에 선언되어 있어야 합니다.
        List<Book> foundBooks = bookRepository.findByTitleContainingIgnoreCaseOrWriterContainingIgnoreCase(query, query);

        log.info("Found {} books matching the query '{}'", foundBooks.size(), query);
        return foundBooks;
    }

}

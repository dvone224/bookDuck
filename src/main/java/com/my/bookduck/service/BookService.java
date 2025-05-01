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
    private final CategoryService categoryService;

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public List<Book> searchBooks(String query, Long mainCategoryId, Long subCategoryId) {
        String trimmedQuery = (query != null) ? query.trim() : null;
        Set<Long> targetCategoryIds = null;
        Long targetId = subCategoryId != null ? subCategoryId : mainCategoryId;

        log.info("Searching books - Query: '{}', MainCategory: {}, SubCategory: {}", trimmedQuery, mainCategoryId, subCategoryId);

        if (subCategoryId != null) {
            targetCategoryIds = Set.of(subCategoryId);
            log.debug("Filtering by specific sub-category ID: {}", subCategoryId);
        } else if (mainCategoryId != null) {
            targetCategoryIds = categoryService.getAllSubCategoryIdsIncludingMain(mainCategoryId);
            log.debug("Filtering by main category ID {} and its sub-categories: {}", mainCategoryId, targetCategoryIds);
            if (targetCategoryIds.isEmpty()) {
                log.warn("Main category {} selected, but no corresponding category IDs found.", mainCategoryId);
                return Collections.emptyList();
            }
        } else {
            log.debug("No category filter applied.");
        }

        return bookRepository.findBooksByQueryAndCategoryIdsIn(trimmedQuery, targetCategoryIds);
    }

    public List<Book> searchBooksByTitleAndUser(String title, Long userId, int limit) {
        log.info("Searching books for user {} with title '{}'", userId, title);
        return bookRepository.findByTitleContainingIgnoreCaseAndUserId(title, userId, limit);
    }

    public Book findById(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with ID: " + bookId));
    }
    public Book getBookById(Long id) throws IllegalStateException {
        log.info("getBookById: {}", id);
        Book book = bookRepository.findBookById(id);
        if (book == null) {
            throw new IllegalStateException("책 정보를 가져오는데 실패했습니다.");
        }
        return book;
    }


}
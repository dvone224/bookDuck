package com.my.bookduck.service;

import com.my.bookduck.controller.request.AddBookRequest;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}

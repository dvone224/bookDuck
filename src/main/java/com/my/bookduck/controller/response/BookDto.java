package com.my.bookduck.controller.response;

import com.my.bookduck.domain.book.Book;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BookDto {
    private final Long id;
    private final String title;
    private final String cover;

    public BookDto(Book book) {
        this.id = book.getId();
        this.title = book.getTitle();
        this.cover = book.getCover();

    }

}

package com.my.bookduck.controller.response;

import com.my.bookduck.domain.book.Book;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;

@ToString
@Getter
public class BookLIstViewResponse {
    private final String title;
    private final String cover;
    private final String writer;
    private final LocalDate publicationDate;
    private final String publishing;
    private final int price;

    public BookLIstViewResponse(Book book) {
        this.title = book.getTitle();
        this.cover = book.getCover();
        this.writer = book.getWriter();
        this.publicationDate = book.getPublicationDate();
        this.publishing = book.getPublishing();
        this.price = book.getPrice();
    }

}

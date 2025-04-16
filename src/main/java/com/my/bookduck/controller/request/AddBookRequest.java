package com.my.bookduck.controller.request;

import com.my.bookduck.domain.book.Book;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AddBookRequest {
    String title;
    String cover;
    String writer;
    LocalDate publicationDate;
    String publishing;
    int price;

    public Book toEntity(AddBookRequest dto){
        return Book.builder()
                .title(dto.getTitle())
                .cover(dto.getCover())
                .writer(dto.getWriter())
                .publicationDate(dto.getPublicationDate())
                .publishing(dto.getPublishing())
                .price(dto.getPrice())
                .build();
    }


}

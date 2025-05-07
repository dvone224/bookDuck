package com.my.bookduck.controller.request;

import com.my.bookduck.domain.book.Book;
import jakarta.persistence.Entity;
import lombok.*;

import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class AdminAddBookRequest {
    private Long id;
    private String title;
    private String cover;
    private String writer;
    private LocalDate publicationDate;
    private String publishing;
    private int price;
    private String identifier;
    private String epubPath;

    public Book toEntity(AdminAddBookRequest dto){
        return Book.adminBuilder()
                .id(dto.id)
                .title(dto.title)
                .cover(dto.cover)
                .writer(dto.writer)
                .publishing(dto.publishing)
                .publicationDate(dto.publicationDate)
                .publishing(dto.publishing)
                .price(dto.price)
                .identifier(dto.identifier)
                .epubPath(dto.epubPath)
                .buildFromAdmin();
    }


}

package com.my.bookduck.domain.book;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"categories", "bookInfos"})
public class Book {
    // 책
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long id;

    private String title;
    private String cover;
    private String writer;
    private LocalDate publicationDate;
    private String publishing;
    private int price;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookCategory> categories;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookInfo> bookInfos;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookComment> comments;

    @Builder
    public Book(String title,String cover,String writer,LocalDate publicationDate,String publishing,int price){
        this.title = title;
        this.cover = cover;
        this.writer = writer;
        this.publicationDate = publicationDate;
        this.publishing = publishing;
        this.price = price;
    }
}

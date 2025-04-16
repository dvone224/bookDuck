package com.my.bookduck.domain.book;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "book")
public class BookInfo {
    // 책 본문
    @Id
    @Column(name = "book_info_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int chapterNum;
    private String chapterTitle;

    @Lob
    private String chapterBody;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

}

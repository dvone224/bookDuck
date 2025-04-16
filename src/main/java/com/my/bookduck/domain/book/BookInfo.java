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
    @Id
    @Column(name = "book_info_id")
    private Long id;

    private int chapterNum;
    private String chapterTitle;
    @Lob
    private String chapterBody;

    @ManyToOne(fetch = FetchType.LAZY)
    private Book book;

}

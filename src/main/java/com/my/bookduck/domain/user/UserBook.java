package com.my.bookduck.domain.user;

import com.my.bookduck.domain.book.Book;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"user", "book"})
@IdClass(UserBookId.class)
public class UserBook {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("bookId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    private String mark;

    @Lob
    private String summary;

    private boolean finish;



}

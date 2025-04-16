package com.my.bookduck.domain.user;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.BookCategoryId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@IdClass(CartId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"user", "book"})
public class Cart {
    @Id
    @Column(name="user_id")
    private Long userId;

    @Id
    @Column(name="book_id")
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;
}

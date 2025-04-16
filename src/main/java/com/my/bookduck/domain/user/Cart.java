package com.my.bookduck.domain.user;

import com.my.bookduck.domain.book.Book;
import jakarta.persistence.*;

@Entity
@IdClass(CartId.class)
public class Cart {
    @Id
    @Column(name="user_id")
    private Long userId;

    @Id
    @Column(name="book_id")
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private Book book;
}

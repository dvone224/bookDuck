package com.my.bookduck.domain.user;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.group.Group;
import jakarta.persistence.*;
import lombok.*;

@Entity
@IdClass(CartId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"user", "book"})
public class Cart {
    @Id
    @Column(name="user_id")
    private Long userId;

    @Id
    @Column(name="book_id")
    private Long bookId;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("bookId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Builder
    public Cart(User user, Book book) {
        this.user = user;
        this.book = book;
    }
}

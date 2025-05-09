package com.my.bookduck.domain.group;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@IdClass(GroupBookId.class)
@ToString(exclude = {"group","book"})
public class GroupBook {
    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @MapsId("groupId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @MapsId("bookId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    private LocalDateTime createdAt;
}

package com.my.bookduck.domain.book;

import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class BookComment {
    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chapterLocation; // 챕터 수
    private String bodyLocation; // 코멘트를 단 글자의 위치(글자수)
    private String comment; // 코멘트 문장
    private LocalDateTime createdAt; // 코멘트를 단 시간

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;
}

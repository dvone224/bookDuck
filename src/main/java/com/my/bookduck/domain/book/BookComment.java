package com.my.bookduck.domain.book; // 실제 패키지 경로로 수정

import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@ToString(exclude = {"group", "user", "book"}) // 순환 참조 방지를 위해 연관관계 필드 제외
public class BookComment {
    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 코멘트가 달린 챕터(스파인 아이템)의 href.
     * 예: "OEBPS/chapter1.xhtml"
     * EPUB.js의 rendition.currentLocation().start.href 또는 section.href 값.
     */
    @Column(length = 512) // 경로가 길 수 있으므로 길이 적절히 설정
    private String chapterHref;

    /**
     * 코멘트가 달린 정확한 위치를 나타내는 EPUB CFI.
     * 예: "epubcfi(/6/14[xchapter_001]!/4/2/16/1:0)"
     * EPUB.js의 rendition.on("selected", (cfiRange, ...)) 에서 cfiRange 값.
     * 이 CFI는 chapterHref 정보도 일부 포함할 수 있지만, chapterHref를 별도로 저장하면
     * 특정 챕터의 모든 코멘트를 조회할 때 더 편리할 수 있습니다.
     */
    @Column(name = "location_cfi", length = 1024) // CFI는 매우 길어질 수 있음
    private String locationCfi;

    @Column(columnDefinition = "TEXT") // 코멘트 내용은 길 수 있으므로 TEXT 타입 고려
    private String comment; // 코멘트 문장

    private LocalDateTime createdAt; // 코멘트를 단 시간

    @Column(length = 20) // 예: #RRGGBB 형식 등 저장
    private String noteColor; // 쪽지 배경 색상

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Builder
    public BookComment(String chapterHref, String locationCfi, String comment, String noteColor, // noteColor 추가
                       LocalDateTime createdAt, Group group, User user, Book book) {
        this.chapterHref = chapterHref;
        this.locationCfi = locationCfi;
        this.comment = comment;
        this.noteColor = noteColor; // 필드 설정
        this.createdAt = createdAt;
        this.group = group;
        this.user = user;
        this.book = book;
    }
}
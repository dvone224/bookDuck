package com.my.bookduck.domain.group;

import com.my.bookduck.domain.board.Board;
import com.my.bookduck.domain.book.Book; // GroupBook에서 사용 (직접 사용 안 함)
import com.my.bookduck.domain.user.User; // GroupUser에서 사용 (직접 사용 안 함)
import com.my.bookduck.domain.book.BookComment;
import jakarta.persistence.*;
import lombok.*; // Setter 포함 또는 필요한 곳에만
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter // 엔티티 상태 변경을 위해 Setter 유지 (혹은 필요한 필드에만 추가)
@NoArgsConstructor // JPA 기본 생성자
@Table(name = "duck_groups")
@ToString(exclude = {"users", "books", "boards", "comments"}) // 연관 필드 제외
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    private String name;

    // --- 컬렉션 필드 초기화 ---
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)// LAZY 유지 권장
    private List<GroupUser> users = new ArrayList<>(); // users는 이미 초기화됨

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private List<GroupBook> books = new ArrayList<>(); // ★★★ books 초기화 추가 ★★★

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private List<Board> boards = new ArrayList<>(); // ★★★ boards 초기화 추가 ★★★

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private List<BookComment> comments = new ArrayList<>(); // ★★★ comments 초기화 추가 ★★★


    @Builder // 이름만 받는 빌더 (다른 필드는 기본 초기값 사용)
    public Group(String name) {
        this.name = name;
        // 다른 컬렉션 필드는 선언부에서 초기화됨
    }

    // 연관관계 편의 메소드 (양방향)
    public void addGroupUser(GroupUser groupUser) {
        this.users.add(groupUser);
        // GroupUser 엔티티에 setGroup이 있고, 무한루프 방지 로직이 있다면 호출
        if (groupUser != null && groupUser.getGroup() != this) {
            groupUser.setGroup(this);
        }
    }

    // 다른 컬렉션 추가 메소드 (필요시 구현)
    public void addGroupBook(GroupBook groupBook) {
        if (this.books == null) { // 방어 코드
            this.books = new ArrayList<>();
        }
        this.books.add(groupBook);
        if (groupBook != null && groupBook.getGroup() != this) {
            groupBook.setGroup(this); // GroupBook에 setGroup 필요
        }
    }

    public void addBoard(Board board) {
        if (this.boards == null) { // 방어 코드
            this.boards = new ArrayList<>();
        }
        this.boards.add(board);
        // Board 엔티티에 setGroup이 있다면 호출
        // if (board != null && board.getGroup() != this) {
        //     board.setGroup(this);
        // }
    }

    public void addComment(BookComment comment) {
        if (this.comments == null) { // 방어 코드
            this.comments = new ArrayList<>();
        }
        this.comments.add(comment);
        // BookComment 엔티티에 setGroup이 있다면 호출
        // if (comment != null && comment.getGroup() != this) {
        //     comment.setGroup(this);
        // }
    }
}
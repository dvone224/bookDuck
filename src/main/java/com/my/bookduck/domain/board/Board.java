package com.my.bookduck.domain.board;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.group.Group;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate; // JPA Auditing 사용 시
import org.springframework.data.jpa.domain.support.AuditingEntityListener; // JPA Auditing 사용 시

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA를 위한 protected 기본 생성자 유지
@ToString(exclude = {"group", "book", "likes"}) // likes도 추가 (Lazy Loading 고려)
@EntityListeners(AuditingEntityListener.class) // JPA Auditing 활성화 시 추가 (createdAt 자동 설정)
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // ID 자동 생성
    @Column(name = "board_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false) // Group 없이는 Board 의미 없으므로 필수
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false) // Book 없이는 Board 의미 없으므로 필수
    private Book book;

    // ★★★ 빌더 관련 어노테이션 제거, 필드 선언 시 직접 초기화 ★★★
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BoardLike> likes = new ArrayList<>(); // NullPointerException 방지 위해 초기화

    @CreatedDate // JPA Auditing 사용 시 자동으로 생성 시간 설정
    @Column(updatable = false) // 생성 이후 수정 불가
    LocalDateTime createdAt;

    // --- ★★★ 필수 필드를 받는 Public 생성자 ★★★ ---
    // BoardService 등 외부에서 객체 생성 시 이 생성자 사용
    public Board(Group group, Book book) {
        if (group == null || book == null) {
            throw new IllegalArgumentException("Board 생성 시 Group과 Book은 필수입니다.");
        }
        this.group = group;
        this.book = book;
        // this.likes는 필드 선언 시 초기화됨
        // this.createdAt은 @CreatedDate에 의해 자동 설정됨 (Auditing 활성화 시)
        // Auditing 미사용 시: this.createdAt = LocalDateTime.now();
    }

    // 만약 createdAt을 Auditing으로 처리하지 않고 수동 설정하려면 @PrePersist 사용 가능
    /*
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
    */

    // Setter는 필요에 따라 추가 (일반적으로 엔티티 필드는 불변성을 유지하는 것이 좋음)
    // 예: public void setGroup(Group group) { this.group = group; }
    // 만약 JPA Auditing을 사용하지 않아 createdAt을 수동으로 설정해야 한다면, Setter가 필요할 수 있음.
    // public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
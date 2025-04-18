package com.my.bookduck.domain.group;

import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*; // Setter, Builder 등 추가

@Entity
@Getter
@Setter // Service에서 필드 설정 위해 추가 (또는 생성자/빌더 사용)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자
@IdClass(GroupUserId.class)
@ToString(exclude = {"group","user"})
public class GroupUser {
    @Id
    @Column(name = "group_id", insertable = false, updatable = false) // FK 매핑 위임
    private Long groupId;

    @Id
    @Column(name = "user_id", insertable = false, updatable = false) // FK 매핑 위임
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id") // 이 컬럼이 실제 DB의 FK
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // 이 컬럼이 실제 DB의 FK
    private User user;

    @Enumerated(EnumType.STRING) // Enum 타입을 문자열로 저장
    private Role role;

    // Role Enum을 public으로 변경하거나, GroupUser 내부에서 접근 가능하게 유지
    public enum Role {
        ROLE_USER, ROLE_ADMIN
    }

    // Service에서 객체 생성을 위한 Builder 추가
    @Builder
    public GroupUser(Group group, User user, Role role) {
        this.group = group;
        this.user = user;
        this.role = role;
        // JPA가 @ManyToOne 관계를 통해 groupId, userId 자동 설정 기대
        // 만약 직접 설정해야 한다면:
        // this.groupId = group.getId();
        // this.userId = user.getId();
    }
}
package com.my.bookduck.domain.group;

import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@IdClass(GroupUserId.class)
@NoArgsConstructor
public class GroupUser {
    @Id // 이 필드는 여전히 복합 키의 일부임을 나타냄
    @Column(name = "group_id") // 실제 컬럼 매핑 (MapsId 때문에 실제 값은 group 필드에서 옴)
    private Long groupId;

    @Id // 이 필드는 여전히 복합 키의 일부임을 나타냄
    @Column(name = "user_id")  // 실제 컬럼 매핑 (MapsId 때문에 실제 값은 user 필드에서 옴)
    private Long userId;

    @MapsId("groupId") // 이 연관관계('group')의 ID를 GroupUser 엔티티의 'groupId' 필드에 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id") // 외래 키 컬럼 지정 (이제 insertable/updatable 가능해야 함)
    private Group group;

    @MapsId("userId") // 이 연관관계('user')의 ID를 GroupUser 엔티티의 'userId' 필드에 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")  // 외래 키 컬럼 지정 (이제 insertable/updatable 가능해야 함)
    private User user;

    @Enumerated(EnumType.STRING) // Enum 타입 저장 방식 명시 권장
    private Role role;

    public enum Role { // public 또는 package-private으로 변경하는 것이 좋을 수 있음
        ROLE_USER, ROLE_ADMIN
    }

    // 생성자나 편의 메소드를 통해 group, user, role 설정 가능
    public GroupUser(Group group, User user, Role role) {
        this.group = group;
        this.user = user;
        this.role = role;
        // groupId와 userId는 @MapsId 덕분에 자동으로 설정될 것이므로 명시적 set 불필요
    }

}

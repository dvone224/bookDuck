package com.my.bookduck.domain.group;

import com.my.bookduck.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@IdClass(GroupUserId.class)
public class GroupUser {
    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Role role;

    private enum Role {
        ROLE_USER, ROLE_ADMIN
    }

}

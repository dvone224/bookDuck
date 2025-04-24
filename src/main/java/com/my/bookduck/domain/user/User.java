package com.my.bookduck.domain.user;

import com.my.bookduck.domain.group.Group;
import com.my.bookduck.domain.book.BookComment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

import static com.my.bookduck.domain.user.User.Role.ROLE_ADMIN;
import static com.my.bookduck.domain.user.User.Role.ROLE_USER;

@Entity
@Getter
@Setter
@NoArgsConstructor  // jpa 만 내 객체를 생성할 수 있게
@ToString(exclude = {"userBooks","carts","groups"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    private String name;
    private String password;
    private String loginId;
    private String nickName;
    private String email;
    private String img;
    @Enumerated(EnumType.STRING)
    private Role role;
    private LocalDateTime created;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserBook> userBooks;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Cart> carts;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BookComment> comments;

    public enum Role {
        ROLE_USER, ROLE_ADMIN
    }

    // 2025. 04. 17 추가 _ 내연
    private String provider;
    private String providerId;

    @Builder
    public User(String name, String password, String loginId, String nickName, String email, String img, String role, String provider, String providerId) {
        if(loginId == null) this.loginId = name;
        else this.loginId = loginId;

        this.name = name;
        this.password = password;
        this.email = email;
        if(loginId.equals("admin"))this.role = ROLE_ADMIN;
        else if(role == null) this.role = ROLE_USER;
        else this.role = Role.valueOf(role);

        //if(nickName == null) this.nickName = name;
        //else this.nickName = nickName;

        this.nickName = nickName;

        this.img = img;

        this.provider = provider;
        this.providerId = providerId;

    }

}

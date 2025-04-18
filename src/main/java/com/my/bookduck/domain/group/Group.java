package com.my.bookduck.domain.group;

import com.my.bookduck.domain.board.Board;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.domain.book.BookComment;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "duck_groups")
@ToString(exclude = {"users","books","boards","comments"})
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;
    private String name;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupUser> users = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupBook> books;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Board> boards;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookComment> comments;


    @Builder
    public Group(String name) {
        this.name = name;
    }

    public void addGroupUser(GroupUser groupUser) {
        this.users.add(groupUser);
        groupUser.setGroup(this); // GroupUser 엔티티에 setGroup 메소드 필요
    }
}

package com.my.bookduck.domain.group;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "group")
public class GroupBook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="group_book_id")
    private Long id;

    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

}

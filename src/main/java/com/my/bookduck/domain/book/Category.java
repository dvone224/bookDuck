package com.my.bookduck.domain.book;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id
    @Column(name = "category_id")
    private Long id;

    private String name;

    // 상위 카테고리 (대분류 → NULL, 소분류 → 대분류의 ID)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // 하위 카테고리 목록 (소분류들)
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Category> children;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookCategory> bookCategories;

    // 생성자 (편의 메서드)
    public Category(Long id, String name, Category parent) {
        this.id = id;
        this.name = name;
        this.parent = parent;
    }
}

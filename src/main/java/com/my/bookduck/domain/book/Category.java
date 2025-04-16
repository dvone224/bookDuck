package com.my.bookduck.domain.book;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    private String name;

    // 상위 카테고리 (대분류 → NULL, 소분류 → 대분류의 ID)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // 하위 카테고리 목록 (소분류들)
    @OneToMany(mappedBy = "parent")
    private List<Category> children;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookCategory> bookCategories;

    // 생성자 (편의 메서드)
    public Category(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }
}

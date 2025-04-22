package com.my.bookduck.controller.response; // 적절한 패키지

import com.my.bookduck.domain.book.Category;
import lombok.Getter;

@Getter
public class CategorySimpleDto {
    private Long id;
    private String name;
    // 부모 ID만 포함 (객체 전체 X)
    private Long parentId;

    public CategorySimpleDto(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        // parent 객체가 null이 아닐 때만 parentId 설정
        this.parentId = (category.getParent() != null) ? category.getParent().getId() : null;
    }
}
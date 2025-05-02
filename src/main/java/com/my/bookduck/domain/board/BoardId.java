package com.my.bookduck.domain.board;

import java.io.Serializable;
import java.util.Objects;

public class BoardId implements Serializable {
    private Long group; // Board 엔티티의 'group' 필드명과 일치
    private Long book;  // Board 엔티티의 'book' 필드명과 일치

    public BoardId() {}

    public BoardId(Long group, Long book) {
        this.group = group;
        this.book = book;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoardId boardId = (BoardId) o;
        return Objects.equals(group, boardId.group) && Objects.equals(book, boardId.book);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, book);
    }
    // Getter/Setter 생략 가능 (Lombok @Data 등으로 대체 가능)
}
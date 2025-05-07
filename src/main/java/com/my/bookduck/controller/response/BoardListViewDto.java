// 예: BoardListViewDto.java
package com.my.bookduck.controller.response; // 실제 패키지 경로

import com.my.bookduck.domain.board.Board;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class BoardListViewDto {
    private Long boardId;
    private String bookTitle;
    private String bookCover;
    private String groupName;
    private Long groupId; // 그룹 상세 페이지 이동 등에 필요할 수 있음
    private Long bookId;  // 책 상세 페이지 이동 등에 필요할 수 있음
    private LocalDateTime createdAt;

    public BoardListViewDto(Board board) {
        this.boardId = board.getId();
        if (board.getBook() != null) {
            this.bookTitle = board.getBook().getTitle();
            this.bookCover = board.getBook().getCover();
            this.bookId = board.getBook().getId();
        } else {
            this.bookTitle = "도서 정보 없음";
            this.bookCover = "/img/default_book_cover.png"; // 기본 이미지
            this.bookId = null;
        }
        if (board.getGroup() != null) {
            this.groupName = board.getGroup().getName();
            this.groupId = board.getGroup().getId();
        } else {
            this.groupName = "그룹 정보 없음";
            this.groupId = null;
        }
        this.createdAt = board.getCreatedAt();
    }
}
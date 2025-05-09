package com.my.bookduck.controller.response;

import com.my.bookduck.domain.book.BookComment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookCommentHighlightDto {
    private Long id; // 코멘트 ID (클릭 시 필요할 수 있음)
    private String locationCfi;
    private String noteColor;
    // private String commentText; // 클릭 시 보여줄 텍스트 (선택적)

    public BookCommentHighlightDto(Long id, String locationCfi, String noteColor) {
        this.id = id;
        this.locationCfi = locationCfi;
        this.noteColor = noteColor;
    }
    // 필요시 생성자나 정적 팩토리 메소드 추가
    public static BookCommentHighlightDto fromEntity(BookComment comment) {
        return new BookCommentHighlightDto(
                comment.getId(),
                comment.getLocationCfi(),
                comment.getNoteColor()
        );
    }
}
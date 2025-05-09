package com.my.bookduck.controller.request;

import com.my.bookduck.domain.book.BookComment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookCommentHighlightDto {
    private Long id;           // 코멘트 ID (하이라이트 클릭 시 사용)
    private String locationCfi; // 하이라이트 위치 CFI
    private String noteColor;   // 하이라이트 색상

    // 엔티티를 DTO로 변환하는 정적 팩토리 메소드 (서비스 등에서 사용)
    public static BookCommentHighlightDto fromEntity(BookComment comment) {
        if (comment == null) {
            return null;
        }
        return new BookCommentHighlightDto(
                comment.getId(),
                comment.getLocationCfi(),
                comment.getNoteColor() // BookComment 엔티티에 getNoteColor() 메소드가 있어야 함
        );
    }
}
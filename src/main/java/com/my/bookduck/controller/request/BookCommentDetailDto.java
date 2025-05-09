package com.my.bookduck.controller.request;

import com.my.bookduck.domain.book.BookComment;
import com.my.bookduck.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookCommentDetailDto {
    private Long id;            // 코멘트 ID
    private String comment;     // 코멘트 내용
    private String nickname;    // 작성자 닉네임
    private String noteColor;   // 쪽지 색상
    private String fontFamily;  // 글꼴 정보 (엔티티에 해당 필드가 있다면)

    public BookCommentDetailDto(Long id, String comment, String nickName, String noteColor) {
        this.id = id;
        this.comment = comment;
        this.noteColor = noteColor;
        this.nickname = nickName;
    }
    // 필요시 추가 정보 (예: 생성 시간, 프로필 이미지 URL 등)
    // private LocalDateTime createdAt;
    // private String userProfileImageUrl;

    // 엔티티를 DTO로 변환하는 정적 팩토리 메소드
    public static BookCommentDetailDto fromEntity(BookComment comment) {
        if (comment == null) {
            return null;
        }
        return new BookCommentDetailDto(
                comment.getId(),
                comment.getComment(),
                // User 객체 및 닉네임 null 체크 필수
                (comment.getUser() != null) ? comment.getUser().getNickName() : "알 수 없는 사용자",
                comment.getNoteColor() // getNoteColor() 필요
                // comment.getFontFamily() // getFontFamily() 필요 (엔티티에 필드가 있다면)
                // , comment.getCreatedAt() // 필요시 생성 시간 추가
                // , (comment.getUser() != null) ? comment.getUser().getProfileImageUrl() : null // 프로필 이미지 URL 추가
        );
    }
}
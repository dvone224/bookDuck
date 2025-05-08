package com.my.bookduck.controller.request;


import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {
    private Long bookId;        // 어느 책에 대한 코멘트인지
    private String cfi;         // 코멘트가 달린 정확한 위치 (bodyLocation)
    private String chapterHref; // 코멘트가 달린 챕터 (chapterLocation)
    private String comment;     // 사용자가 입력한 코멘트 내용
}

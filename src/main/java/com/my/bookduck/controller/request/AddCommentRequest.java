package com.my.bookduck.controller.request;


import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {
    private Long bookId;
    private String cfi;
    private String chapterHref;
    private String comment;
    private String noteColor;
    private String fontFamily;
}

package com.my.bookduck.controller.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor // JSON 역직렬화를 위해 Jackson 라이브러리가 기본 생성자를 사용합니다.
@ToString // 로깅 시 객체 내용을 쉽게 확인하기 위함 (선택 사항)
public class UpdateUserBookMarkRequest {
    private Long userId; // 클라이언트에서 전송하는 사용자 ID
    private Long bookId; // 클라이언트에서 전송하는 책 ID
    private String mark; // 클라이언트에서 전송하는 CFI (Canonical Fragment Identifier) 문자열
}
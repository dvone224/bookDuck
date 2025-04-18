package com.my.bookduck.controller.response; // 또는 적절한 DTO 패키지

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResultResponse {
    private Long id;
    private String nickName;
    // 필요시 다른 필드(예: nickName, email 등) 추가 가능
}
package com.my.bookduck.controller.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinishRequestDto {
    private Long userId;
    private Long bookId;
}

package com.my.bookduck.controller.response;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BookSimpleDto {
    private final Long id;
    private final String title;
}

package com.my.bookduck.domain.user;

import com.my.bookduck.domain.book.Book;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class CartId implements Serializable {
    private Long userId;
    private Long bookId;
}

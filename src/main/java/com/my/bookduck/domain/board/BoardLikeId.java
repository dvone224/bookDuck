package com.my.bookduck.domain.board;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class BoardLikeId {
    private Long boardId;
    private Long userId;
}

package com.my.bookduck.domain.group;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class GroupBookId implements Serializable {
    private Long groupId;
    private Long bookId;
}

package com.my.bookduck.domain.board;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class BookBoard {
    @Id
    @Column(name = "book_board_id")
    private Long id;

}

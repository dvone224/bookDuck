package com.my.bookduck.repository;

import com.my.bookduck.domain.book.BookComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCommentRepository extends JpaRepository<BookComment, Long> {
}

package com.my.bookduck.repository;

import com.my.bookduck.domain.book.BookComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookCommentRepository extends JpaRepository<BookComment, Long> {
    List<BookComment> findByBookIdAndChapterHref(Long bookId, String chapterHref);
}

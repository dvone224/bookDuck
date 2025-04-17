package com.my.bookduck.repository;

import com.my.bookduck.domain.book.BookInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookInfoRepository extends JpaRepository<BookInfo, Long> {
    BookInfo findByBookIdAndChapterNum(Long book_id, int chapter_num);
}

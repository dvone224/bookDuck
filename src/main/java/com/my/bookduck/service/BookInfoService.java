package com.my.bookduck.service;

import com.my.bookduck.domain.book.BookInfo;
import com.my.bookduck.repository.BookInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookInfoService {

    private final BookInfoRepository bookInfoRepository;

    public String getBookBody(Long book_id) {
        BookInfo bookInfo = bookInfoRepository.findByBookIdAndChapterNum(book_id, 1);

        return bookInfo.getChapterBody();
    }
}

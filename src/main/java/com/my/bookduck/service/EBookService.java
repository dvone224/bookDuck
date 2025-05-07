package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.BookInfo;
import com.my.bookduck.repository.BookInfoRepository;
import com.my.bookduck.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EBookService {

    private final BookInfoRepository bookInfoRepository;
    private final BookRepository bookRepository;
    @Value("${epub.storage.base-path}")
    private Path epubBasePath;

    public String getBookBody(Long book_id) {
        BookInfo bookInfo = bookInfoRepository.findByBookIdAndChapterNum(book_id, 1);

        return bookInfo.getChapterBody();
    }

    // ID로 Book 엔티티를 찾고, 저장된 상대 경로와 기본 경로를 조합하여 전체 Path 반환
    public Path getBookPath(Long id) {
        Optional<Book> bookOptional = bookRepository.findById(id);
        if (bookOptional.isPresent()) {
            String relativePathString = bookOptional.get().getEpubPath();
            if (relativePathString != null && !relativePathString.isEmpty()) {
                // 기본 경로(epubBasePath)와 DB의 상대 경로(relativePathString)를 안전하게 조합
                // resolve() 메소드는 경로 구분자를 자동으로 처리하고 경로 조작(..) 시도 시 예외 발생 가능성 있음
                return epubBasePath.resolve(relativePathString).normalize();
            }
        }
        return null;
    }
}

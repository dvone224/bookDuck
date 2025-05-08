package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.BookInfo;
import com.my.bookduck.repository.BookInfoRepository;
import com.my.bookduck.repository.BookRepository;
import jakarta.annotation.PostConstruct; // PostConstruct 임포트
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver; // 리소스 패턴 리졸버
import org.springframework.stereotype.Service;
// import org.springframework.util.FileCopyUtils; // FileCopyUtils는 이 예제에서 직접 사용하지 않음

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EBookService {

    private final BookInfoRepository bookInfoRepository;
    private final BookRepository bookRepository;

    // application.properties (또는 yml)에서 설정할 임시 디렉토리 경로
    // 예: epub.temp.base-path=/app_data/epubs 또는 C:/app_data/epubs
    // Docker 컨테이너 내에서 사용한다면, 컨테이너 내부의 쓰기 가능한 경로로 설정
    @Value("${epub.temp.base-path:/tmp/bookduck_epubs}") // 기본값: /tmp/bookduck_epubs (Linux/Mac)
    private String tempEpubDirectoryPathString;

    // 실제 파일 시스템의 EPUB 기본 경로를 저장할 Path 객체
    // 이 필드는 @PostConstruct 메소드에서 초기화됩니다.
    private Path fileSystemEpubBasePath;

    @PostConstruct
    public void initializeEpubResources() {
        try {
            // 설정된 문자열 경로를 Path 객체로 변환
            this.fileSystemEpubBasePath = Paths.get(tempEpubDirectoryPathString).toAbsolutePath();

            // 임시 디렉토리 생성 (존재하지 않는 경우)
            if (!Files.exists(this.fileSystemEpubBasePath)) {
                Files.createDirectories(this.fileSystemEpubBasePath);
                log.info("Created temporary EPUB base directory at: {}", this.fileSystemEpubBasePath);
            } else {
                log.info("Using existing temporary EPUB base directory at: {}", this.fileSystemEpubBasePath);
            }

            // JAR 내부의 static/epubs 리소스를 임시 디렉토리로 복사
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // "classpath:/static/epubs/**" 패턴은 JAR 내부의 src/main/resources/static/epubs/ 아래 모든 파일을 찾음
            Resource[] resources = resolver.getResources("classpath:/static/epubs/**");

            log.info("Found {} resources in classpath:/static/epubs/", resources.length);

            for (Resource resource : resources) {
                if (resource.isReadable() && resource.exists()) { // 리소스가 읽을 수 있고 존재하는지 확인
                    String resourceUriString = resource.getURI().toString();
                    String relativePath = "";

                    // 'static/epubs/' 이후의 상대 경로 추출
                    // 예: jar:file:/app/server.jar!/BOOT-INF/classes!/static/epubs/dir1/book1.epub
                    // -> dir1/book1.epub
                    int indexOfStaticEpubs = resourceUriString.indexOf("static/epubs/");
                    if (indexOfStaticEpubs != -1) {
                        relativePath = resourceUriString.substring(indexOfStaticEpubs + "static/epubs/".length());
                    } else if (resource.getFilename() != null && !resource.getFilename().isEmpty()) {
                        // 위의 방법으로 상대 경로를 못찾을 경우 파일 이름이라도 사용 (단, 디렉토리 구조가 깨질 수 있음)
                        // 이 부분은 리소스 구조에 따라 더 정교한 로직이 필요할 수 있습니다.
                        relativePath = resource.getFilename();
                        log.warn("Could not determine relative path for resource: {}, using filename: {}", resourceUriString, relativePath);
                    }

                    // 상대 경로가 비어있거나 (예: 디렉토리 자체) 파일 이름이 없는 경우 건너뜀
                    if (relativePath == null || relativePath.isEmpty() || relativePath.endsWith("/")) {
                        log.debug("Skipping resource (likely a directory or empty path): {}", resourceUriString);
                        continue;
                    }

                    Path targetFile = this.fileSystemEpubBasePath.resolve(relativePath);

                    // 대상 파일의 부모 디렉토리 생성
                    Files.createDirectories(targetFile.getParent());

                    try (InputStream inputStream = resource.getInputStream()) {
                        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Copied EPUB resource: {} to {}", resource.getFilename(), targetFile);
                    } catch (IOException e) {
                        log.error("Failed to copy resource: {} to {}", resource.getFilename(), targetFile, e);
                    }
                } else {
                    log.warn("Resource is not readable or does not exist: {}", resource.getURI());
                }
            }
            log.info("EPUB resources initialization complete. Base path: {}", this.fileSystemEpubBasePath);

        } catch (IOException e) {
            log.error("Critical error during EPUB resource initialization. EPUB functionality may be impaired.", e);
            // 이 경우, fileSystemEpubBasePath가 null로 남아있거나 유효하지 않은 상태일 수 있습니다.
            // getBookPath 등에서 null 체크를 통해 안전하게 처리해야 합니다.
            // 또는 애플리케이션 실행을 중단시키는 것도 고려할 수 있습니다.
            // throw new RuntimeException("Failed to initialize EPUB resources", e);
        }
    }

    public String getBookBody(Long book_id) {
        // DB에서 BookInfo를 찾을 때 chapterNum이 1이 아닌 경우도 고려해야 할 수 있습니다.
        BookInfo bookInfo = bookInfoRepository.findByBookIdAndChapterNum(book_id, 1);
        if (bookInfo == null) {
            log.warn("BookInfo not found for book_id: {} and chapterNum: 1", book_id);
            return null; // 또는 적절한 예외 처리
        }
        return bookInfo.getChapterBody();
    }

    // ID로 Book 엔티티를 찾고, 저장된 상대 경로와 파일 시스템 기본 경로를 조합하여 전체 Path 반환
    public Path getBookPath(Long id) {
        // fileSystemEpubBasePath가 @PostConstruct에서 초기화되지 않았거나 실패했을 경우를 대비
        if (this.fileSystemEpubBasePath == null) {
            log.error("EPUB base path (fileSystemEpubBasePath) is not initialized. Cannot get book path for id: {}", id);
            return null;
        }

        Optional<Book> bookOptional = bookRepository.findById(id);
        if (bookOptional.isPresent()) {
            Book book = bookOptional.get();
            String relativePathString = book.getEpubPath(); // Book 엔티티에 epub 파일의 상대 경로가 저장되어 있다고 가정

            if (relativePathString != null && !relativePathString.isEmpty()) {
                // 파일 시스템 기본 경로와 DB의 상대 경로를 조합
                Path fullPath = this.fileSystemEpubBasePath.resolve(relativePathString).normalize();
                log.debug("Resolved book path for id {}: {}", id, fullPath);

                // 실제로 파일이 존재하는지 확인 (선택 사항이지만 권장)
                if (!Files.exists(fullPath)) {
                    log.warn("EPUB file does not exist at resolved path: {} (Original relative path: {})", fullPath, relativePathString);
                    // 여기에 추가적인 디버깅 로그나 처리를 넣을 수 있습니다.
                    // 예: this.fileSystemEpubBasePath 아래의 파일 목록을 로깅
                    try {
                        Files.list(this.fileSystemEpubBasePath).forEach(path -> log.debug("File in temp dir: {}", path));
                    } catch (IOException e) {
                        log.error("Error listing files in temp dir", e);
                    }
                    return null; // 파일이 없으면 null 반환 또는 예외 발생
                }
                return fullPath;
            } else {
                log.warn("Book with id {} has an empty or null epubPath in the database.", id);
            }
        } else {
            log.warn("Book not found with id: {}", id);
        }
        return null; // 책을 못 찾거나 경로 정보가 없으면 null 반환
    }
}
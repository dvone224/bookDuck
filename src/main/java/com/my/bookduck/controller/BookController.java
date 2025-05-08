package com.my.bookduck.controller;

import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.controller.response.BookLIstViewResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.Category;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.UserRepository;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CategoryService;
import com.my.bookduck.service.EBookService;
import com.my.bookduck.service.UserBookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final EBookService eBookService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final UserBookService userBookService;

    @GetMapping("/books")
    public String listOrSearchBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        log.info("====== Full Page Load Request ======");
        log.info("GET /books - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        Long finalMainCategoryId = null;
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            finalSubCategoryId = categoryId;
            Optional<Category> subCatOpt = categoryService.findById(categoryId);
            if (subCatOpt.isPresent() && subCatOpt.get().getParent() != null) {
                finalMainCategoryId = subCatOpt.get().getParent().getId();
            } else {
                finalMainCategoryId = mainCategoryIdParam;
                log.warn("SubCategory {} found, but parent category could not be determined or mainCategoryIdParam ({}) provided.", categoryId, mainCategoryIdParam);
            }
            log.debug("Filtering by SubCategory ID: {}, Determined MainCategory ID for display: {}", finalSubCategoryId, finalMainCategoryId);
        } else if (mainCategoryIdParam != null) {
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
            log.debug("Filtering by MainCategory ID: {}", finalMainCategoryId);
        } else {
            log.debug("No category filter applied.");
        }

        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        String pageTitle;
        String message = null;
        String trimmedQuery = query.trim();
        Category titleCategory = null;

        if (finalSubCategoryId != null) {
            titleCategory = categoryService.findById(finalSubCategoryId).orElse(null);
        } else if (finalMainCategoryId != null) {
            titleCategory = categoryService.findById(finalMainCategoryId).orElse(null);
        }

        if (!trimmedQuery.isEmpty() || titleCategory != null) {
            StringBuilder titleBuilder = new StringBuilder();
            if (titleCategory != null) {
                titleBuilder.append("[").append(titleCategory.getName()).append("] ");
            }
            if (!trimmedQuery.isEmpty()) {
                titleBuilder.append("'").append(trimmedQuery).append("' ");
            }
            titleBuilder.append("검색 결과");
            pageTitle = titleBuilder.toString();
            if (booksResult.isEmpty()) {
                message = "조건에 맞는 검색 결과가 없습니다.";
            }
        } else {
            pageTitle = "전체 도서 목록";
            if (booksResult.isEmpty()) {
                message = "등록된 도서가 없습니다.";
            }
        }

        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList());

        List<Category> mainCategories = categoryService.getMainCategories();
        List<Category> subCategories = List.of();
        if (finalMainCategoryId != null) {
            subCategories = categoryService.getSubCategories(finalMainCategoryId);
        }

        model.addAttribute("list", responseList);
        model.addAttribute("searchQuery", query);
        if (message != null) {
            model.addAttribute("message", message);
        }
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("mainCategories", mainCategories);
        model.addAttribute("subCategories", subCategories);
        model.addAttribute("selectedMainCategoryId", finalMainCategoryId);
        model.addAttribute("selectedSubCategoryId", finalSubCategoryId);

        log.debug("Model attributes for full page - selectedMainCategoryId: {}, selectedSubCategoryId: {}", model.getAttribute("selectedMainCategoryId"), model.getAttribute("selectedSubCategoryId"));

        return "book/booklist";
    }

    @GetMapping("/read/{id}")
    public String ebookReaderPage(@PathVariable Long id, Model model) {
        model.addAttribute("bookId", id);
        Book book = bookService.findById(id);
        if (book != null) {
            model.addAttribute("bookTitle", book.getTitle());
        } else {
            model.addAttribute("bookTitle", "Ebook Reader");
        }
        return "book/viewer";
    }

    @CrossOrigin
    @GetMapping("/api/books/epub/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveEpubFile(@PathVariable Long id) {
        Path filePath = eBookService.getBookPath(id);
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Epub file path not found or does not exist for id: {}", id);
            return ResponseEntity.notFound().build();
        }
        log.info("Serving whole Epub file (direct request): {}", filePath);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/epub+zip"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                log.error("Could not read epub file: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("MalformedURLException for epub file path: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @CrossOrigin
    @GetMapping("/api/epub-content/{id}/**")
    public ResponseEntity<Resource> serveEpubInternalContent(
            @PathVariable Long id,
            HttpServletRequest request) {

        Path epubFilePath = eBookService.getBookPath(id);
        // log.info("EPUB file path for ID {}: {}", id, epubFilePath); // 초기 경로 로깅은 유지하거나 필요시 주석 처리

        if (epubFilePath == null || !Files.exists(epubFilePath)) {
            log.warn("EPUB file not found for ID: {}. Searched at: {}", id, epubFilePath);
            return ResponseEntity.notFound().build();
        }

        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String baseUriPattern = contextPath + "/book/api/epub-content/" + id + "/";
        String internalPath;

        if (requestUri.startsWith(baseUriPattern)) {
            internalPath = requestUri.substring(baseUriPattern.length());
        } else {
            log.error("Request URI {} does not match expected base pattern {}", requestUri, baseUriPattern);
            return ResponseEntity.badRequest().body(null);
        }

        try {
            internalPath = URLDecoder.decode(internalPath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            log.error("Error decoding internal path: {}", internalPath, e);
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            log.error("Error decoding internal path (possibly invalid UTF-8): {}", internalPath, e);
            return ResponseEntity.badRequest().build();
        }

        if (internalPath.equalsIgnoreCase("book.epub")) {
            log.debug("Request for 'book.epub' (ID: {}) - this path might be deprecated if client uses base directory.", id);
            return ResponseEntity.ok().build();
        }

        if (internalPath.isEmpty()) {
            log.debug("Request for empty internal path for ID: {}. This is an initial probe by epub.js. Responding with 200 OK.", id);
            return ResponseEntity.ok().build();
        }

        // META-INF/com.apple.ibooks.display-options.xml 요청에 대한 특별 처리 (선택 사항)
        if (internalPath.equalsIgnoreCase("META-INF/com.apple.ibooks.display-options.xml")) {
            log.warn("Requested optional Apple iBooks display options file for ID {}: {}. This file is often not present. Returning 404.", id, internalPath);
            return ResponseEntity.notFound().build(); // 또는 빈 200 OK를 반환할 수도 있음: ResponseEntity.ok().build();
        }


        log.debug("Attempting to serve internal path '{}' from EPUB ID {}", internalPath, id);

        try (FileSystem zipFs = FileSystems.newFileSystem(epubFilePath, (ClassLoader) null)) {
            Path pathInZip = zipFs.getPath(internalPath);

            if (Files.exists(pathInZip) && Files.isReadable(pathInZip)) {
                byte[] fileContent = Files.readAllBytes(pathInZip);

                String lowerInternalPath = internalPath.toLowerCase();
                // 텍스트 기반 파일에 대한 상세 로깅 (내용 미리보기 길이 조절)
                if (lowerInternalPath.endsWith(".xml") || lowerInternalPath.endsWith(".opf") ||
                        lowerInternalPath.endsWith(".xhtml") || lowerInternalPath.endsWith(".ncx") ||
                        lowerInternalPath.endsWith(".css") || lowerInternalPath.endsWith(".js")) { // CSS, JS도 추가
                    try {
                        int logContentLength = Math.min(fileContent.length, 2048); // OPF, XHTML 등은 내용이 길 수 있으므로 로그 길이를 2KB까지 허용
                        // UTF-8이 아닐 수도 있는 경우를 대비하여, 실제 인코딩을 알 수 없다면 기본 인코딩을 사용하거나,
                        // 혹은 문제가 될 경우 로깅을 생략하는 것이 나을 수 있음. 여기서는 UTF-8 가정.
                        String contentForLogging = new String(fileContent, 0, logContentLength, StandardCharsets.UTF_8);
                        if (fileContent.length > logContentLength) {
                            contentForLogging += "\n[...content truncated in log...]";
                        }

                        if (lowerInternalPath.endsWith(".opf")) {
                            log.info("***** Serving OPF File Content for '{}' (Book ID {}). Length: {}. Content:\n{}",
                                    internalPath, id, fileContent.length, contentForLogging);
                        } else {
                            log.info("Serving text content of '{}' (Book ID {}). Length: {}. Logged content preview:\n{}",
                                    internalPath, id, fileContent.length, contentForLogging);
                        }
                    } catch (Exception e) { // 문자열 변환 중 예외 발생 가능성 고려
                        log.warn("Could not convert content of '{}' (Book ID {}) to string for logging. Serving as binary. Length: {}",
                                internalPath, id, fileContent.length, e);
                    }
                } else { // 이미지, 폰트 등 바이너리 파일
                    log.info("Serving binary content of '{}' (Book ID {}). Content length: {}.",
                            internalPath, id, fileContent.length);
                }

                Resource resource = new ByteArrayResource(fileContent);
                String mimeType = determineMimeType(internalPath);
                CacheControl cacheControl = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentLength(fileContent.length);

                return ResponseEntity.ok()
                        .headers(responseHeaders)
                        .contentType(MediaType.parseMediaType(mimeType))
                        .cacheControl(cacheControl)
                        .body(resource);
            } else {
                log.warn("Internal path '{}' not found in EPUB ID {} (Path in Zip: {}). EPUB file: {}", internalPath, id, pathInZip, epubFilePath);
                return ResponseEntity.notFound().build();
            }
        } catch (NoSuchFileException nsfe) {
            log.warn("Internal path (NoSuchFileException) '{}' not found in EPUB ID {}: {}. EPUB file: {}", internalPath, id, nsfe.getMessage(), epubFilePath);
            return ResponseEntity.notFound().build();
        }
        catch (ProviderNotFoundException pnfe) {
            log.error("Zip FileSystem provider not found for EPUB ID {}. This is unusual. EPUB file: {}", id, epubFilePath, pnfe);
            return ResponseEntity.internalServerError().build();
        }
        catch (IOException e) {
            log.error("IOException while serving internal path '{}' from EPUB ID {}: {}. EPUB file: {}", internalPath, id, e.getMessage(), epubFilePath, e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error while serving internal path '{}' from EPUB ID {}: {}. EPUB file: {}", internalPath, id, e.getMessage(), epubFilePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String determineMimeType(String path) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".xml") || lowerPath.endsWith(".opf") || lowerPath.endsWith(".ncx")) {
            return "application/xml";
        } else if (lowerPath.endsWith(".xhtml") || lowerPath.endsWith(".html")) {
            return "application/xhtml+xml";
        } else if (lowerPath.endsWith(".css")) {
            return "text/css";
        } else if (lowerPath.endsWith(".js")) {
            return "application/javascript";
        } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerPath.endsWith(".svg") || lowerPath.endsWith(".svgz")) {
            return "image/svg+xml";
        } else if (lowerPath.endsWith(".otf")) {
            return "font/otf";
        } else if (lowerPath.endsWith(".ttf")) {
            return "font/ttf";
        } else if (lowerPath.endsWith(".woff")) {
            return "font/woff";
        } else if (lowerPath.endsWith(".woff2")) {
            return "font/woff2";
        }
        // EPUB에서 사용될 수 있는 추가적인 오디오/비디오 타입 (필요하다면)
        // else if (lowerPath.endsWith(".mp3")) { return "audio/mpeg"; }
        // else if (lowerPath.endsWith(".mp4")) { return "video/mp4"; }
        log.warn("Unknown MIME type for path: '{}'. Defaulting to octet-stream.", path);
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }


    @GetMapping("/books/filter")
    public String filterBooks(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestParam(value = "mainCategoryIdParam", required = false) Long mainCategoryIdParam,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        log.info("====== AJAX Filter Request ======");
        log.info("GET /books/filter - Query: '{}', MainCategoryIDParam: {}, CategoryID: {}", query, mainCategoryIdParam, categoryId);

        Long finalMainCategoryId = null;
        Long finalSubCategoryId = null;

        if (categoryId != null) {
            finalSubCategoryId = categoryId;
            Optional<Category> subCatOpt = categoryService.findById(categoryId);
            if (subCatOpt.isPresent() && subCatOpt.get().getParent() != null) {
                finalMainCategoryId = subCatOpt.get().getParent().getId();
            } else {
                finalMainCategoryId = mainCategoryIdParam;
            }
            log.debug("AJAX: Filtering by SubCategory ID: {}", finalSubCategoryId);
        } else if (mainCategoryIdParam != null) {
            finalMainCategoryId = mainCategoryIdParam;
            finalSubCategoryId = null;
            log.debug("AJAX: Filtering by MainCategory ID: {}", finalMainCategoryId);
        } else {
            log.debug("AJAX: No category filter applied.");
        }

        List<Book> booksResult = bookService.searchBooks(query, finalMainCategoryId, finalSubCategoryId);

        List<BookLIstViewResponse> responseList = booksResult.stream()
                .map(BookLIstViewResponse::new)
                .collect(Collectors.toList());

        model.addAttribute("list", responseList);
        log.info("Returning HTML fragment for #bookTableBody with {} books.", responseList.size());
        return "book/booklist :: #bookTableBody";
    }

    @GetMapping("/api/categories/{parentId}/subcategories")
    @ResponseBody
    public List<Category> getSubCategoriesApi(@PathVariable Long parentId) {
        log.debug("API request for subcategories of parentId: {}", parentId);
        return categoryService.getSubCategories(parentId);
    }

    private record BookDTO(Long id, String title, String cover) {}

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<BookDTO>> searchBooks(
            @RequestParam String title,
            @RequestParam(defaultValue = "8") int limit,
            Principal principal) { // Principal을 사용하여 현재 사용자 식별
        if (principal == null) {
            log.warn("No authenticated user found for book search");
            // 401 Unauthorized 반환 또는 로그인 페이지 리다이렉션 유도 (클라이언트 처리 필요)
            return ResponseEntity.status(401).body(List.of());
        }

        String loginId = principal.getName(); // Spring Security에서 사용자 ID (username) 가져오기
        log.debug("Searching books for user with loginId: {}", loginId);

        // loginId로 User 엔티티 찾기
        User user = userRepository.findByLoginId(loginId); // findByLoginId 구현 필요
        if (user == null) {
            log.error("User not found for loginId: {}", loginId);
            // 404 Not Found 또는 적절한 오류 응답
            return ResponseEntity.status(404).body(List.of());
        }

        Long userId = user.getId(); // User 엔티티에서 실제 ID 가져오기
        List<Book> books = bookService.searchBooksByTitleAndUser(title, userId, limit); // 서비스 호출

        // ★★★ BookDTO 생성 시 cover 정보 포함 ★★★
        List<BookDTO> bookDTOs = books.stream()
                .map(book -> new BookDTO(book.getId(), book.getTitle(), book.getCover())) // cover 추가
                .toList();
        log.debug("Found {} books for userId: {} with title '{}'", bookDTOs.size(), userId, title);
        return ResponseEntity.ok(bookDTOs);
    }


    @GetMapping("/myBook")
    public String showMyBookList(Model model, @AuthenticationPrincipal BDUserDetails userDetails) {
        log.info("Request received for user's full book list page.");

        // 1. 로그인 사용자 확인
        if (userDetails == null) {
            log.warn("User not authenticated. Redirecting to login.");
            // 로그인 페이지로 리다이렉트하거나 오류 메시지 전달
            // 예: redirectAttributes.addFlashAttribute("errorMsg", "로그인이 필요합니다.");
            return "redirect:/login-form"; // 로그인 페이지 경로로 수정
        }

        Long userId = userDetails.getUser().getId();
        log.info("Fetching all books for userId: {}", userId);

        try {
            // 2. 사용자의 책 목록 조회 (UserBookService 사용)
            List<Book> myBooks = userBookService.findMyBooks(userId);

            // 3. 조회된 책 목록을 Model에 추가
            model.addAttribute("myBooks", myBooks); // 뷰에서 사용할 이름 "myBooks"

            // 4. (선택적) 페이지 제목 등 추가 정보 전달
            model.addAttribute("pageTitle", "내 서재");

            // 5. (선택적) 책이 없을 경우 메시지 전달
            if (myBooks.isEmpty()) {
                model.addAttribute("message", "내 서재에 등록된 책이 없습니다.");
            }

        } catch (Exception e) {
            // 6. 오류 처리
            log.error("Error fetching user's book list for userId: {}", userId, e);
            model.addAttribute("myBooks", Collections.emptyList()); // 빈 리스트 전달
            model.addAttribute("errorMsg", "내 서재 목록을 불러오는 중 오류가 발생했습니다.");
        }

        // 7. 뷰 이름 반환
        return "book/myBookList"; // templates/book/myBookList.html 파일을 반환
    }


    @GetMapping("/searchbookid")
    public ResponseEntity serchuserid(Long id) {
        log.info("serchbookid: {}", id);

        try{
            Book b = bookService.findBybookid(id);
            if(b == null){
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.notFound().build();
        }catch(Exception e){
            //log.error("errMsg: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

}
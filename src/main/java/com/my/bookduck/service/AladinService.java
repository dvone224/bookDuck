package com.my.bookduck.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.bookduck.controller.response.AladinApiResponse;
import com.my.bookduck.controller.response.AladinBookItem;
import com.my.bookduck.controller.response.PaginatedAladinResponse;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.book.SyncStatus;
import com.my.bookduck.repository.BookRepository;
import com.my.bookduck.repository.SyncStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AladinService {



    // application.properties에서 값 주입
    @Value("${aladin.api.key}")
    private String apiKey;

    @Value("${aladin.api.url}")
    private String apiUrl;

    @Value("${aladin.api.itemlist}")
    private String apiListUrl;

    private final RestTemplate restTemplate;
    private final BookRepository bookRepository;
    private final WebClient webClient;

    public PaginatedAladinResponse searchBooks(String query, int page, int size) {
        if (query == null || query.trim().isEmpty()) {
            // 빈 결과 반환
            return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
        }

        // API 요청 URL 생성 (start와 MaxResults 파라미터 사용)
        URI uri = UriComponentsBuilder
                .fromUriString(apiUrl)
                .queryParam("TTBKey", apiKey)
                .queryParam("Query", query)
                .queryParam("QueryType", "Keyword")
                .queryParam("MaxResults", size)       // 페이지당 항목 수
                .queryParam("start", page)           // 요청할 페이지 번호 (알라딘은 1부터 시작)
                .queryParam("SearchTarget", "eBook") // eBook 검색 유지
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        try {
            // API 호출 및 응답 받기 (AladinApiResponse는 전체 응답 구조 DTO)
            AladinApiResponse response = restTemplate.getForObject(uri, AladinApiResponse.class);

            if (response != null) {
                List<AladinBookItem> books = (response.getItem() != null) ? response.getItem() : Collections.emptyList();
                // 페이징된 결과 객체 생성하여 반환
                return new PaginatedAladinResponse(books, response.getTotalResults(), page, size);
            } else {
                System.out.println("알라딘 API 응답이 null입니다.");
                return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
            }
        } catch (Exception e) {
            System.err.println("알라딘 API 호출 중 오류 발생: " + e.getMessage());
            return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size); // 오류 시 빈 결과 반환
        }
    }

    private final ObjectMapper objectMapper;
    private final SyncStatusRepository syncStatusRepository;

    // --- 상수 정의 ---
    private static final int MAX_RESULTS_PER_PAGE = 50;
    // 알라딘 API 전체 페이지 제한과는 별개로, 한 번 실행 시 가져올 페이지 수 (배치 크기)
    private static final int PAGES_PER_RUN = 20; // 예: 20 페이지 = 약 1000 아이템
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_BESTSELLER_CATEGORY_ID = 0; // 외부에서도 사용할 수 있도록 public static final

    // --- 결과 카운트 및 상태를 위한 DTO ---
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class SyncResult {
        private long totalApiItems = 0; // API에서 조회된 총 아이템 수 (이번 배치)
        private long savedCount = 0;    // 새로 저장된 책 수 (이번 배치)
        private long updatedCount = 0;  // 업데이트된 책 수 (이번 배치)
        private boolean errorOccurred = false; // 에러 발생 여부
        private int startPage = 1; // 이번 배치 시작 페이지
        private int lastAttemptedPage = 0; // 이번 배치에서 시도한 마지막 페이지 번호
        private int actualLastProcessedPage = 0; // 이번 배치에서 실제로 성공 처리된 마지막 페이지 번호

        public void incrementSavedCount() { this.savedCount++; }
        public void incrementUpdatedCount() { this.updatedCount++; }
        public SyncResult setErrorOccurred(boolean errorOccurred) { this.errorOccurred = errorOccurred; return this; }
    }

    /**
     * 알라딘 API에서 특정 카테고리의 *다음* 베스트셀러 배치(PAGES_PER_RUN 개수만큼)를 가져와 DB에 저장/업데이트합니다.
     * DB에 저장된 마지막 처리 페이지 다음부터 시작합니다.
     *
     * @param categoryId 베스트셀러를 조회할 알라딘 카테고리 ID
     * @return Mono<SyncResult> 동기화 결과 (이번 배치에 대한 정보)
     */
    public Mono<SyncResult> fetchNextBestsellerBatch(int categoryId) {
        String syncKey = "bestseller_" + categoryId;
        log.info("알라딘 API 다음 베스트셀러 배치(SyncKey={}) 처리 시작", syncKey);
        SyncResult result = new SyncResult();

        // 1. 마지막 처리 페이지 읽기
        return Mono.fromCallable(() -> syncStatusRepository.findById(syncKey)
                        .map(SyncStatus::getLastProcessedPage)
                        .orElse(0))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(lastProcessedPage -> {
                    int startPage = lastProcessedPage + 1;
                    result.setStartPage(startPage);
                    int pagesToFetchThisRun = PAGES_PER_RUN;
                    result.setLastAttemptedPage(startPage + pagesToFetchThisRun - 1);
                    log.info("[{}] DB 조회 결과 lastProcessedPage={}, 이번 실행 시작 페이지={}, 가져올 페이지 수={}",
                            syncKey, lastProcessedPage, startPage, pagesToFetchThisRun);
                    AtomicInteger currentMaxProcessedPage = new AtomicInteger(lastProcessedPage);

                    // 2. API 호출 및 처리 파이프라인
                    return Flux.range(startPage, pagesToFetchThisRun)
                            .concatMap(pageNumber -> fetchAladinPage(categoryId, pageNumber)
                                            .doOnSuccess(response -> { /* ... 페이지 번호 추적 ... */
                                                if (isResponseValid(response, syncKey, pageNumber)) {
                                                    currentMaxProcessedPage.accumulateAndGet(pageNumber, Math::max);
                                                    if (response.getItem() != null && !response.getItem().isEmpty()) {
                                                        result.setTotalApiItems(result.getTotalApiItems() + response.getItem().size());
                                                    }
                                                }
                                            })
                                            .flatMapMany(pageResponse -> Flux.fromIterable(/* ... 아이템 추출 ... */
                                                    (pageResponse != null && pageResponse.getItem() != null) ? pageResponse.getItem() : Collections.emptyList()
                                            ))
                                            .onErrorResume(e -> { /* ... 개별 페이지 오류 처리 ... */
                                                log.warn("[{}] 페이지 {} 처리 중 오류 발생하여 건너<0xEB><0x9A><0x81>니다: {}", syncKey, pageNumber, e.getMessage());
                                                return Flux.empty();
                                            }),
                                    1)
                            .filter(this::isValidBookItem)
                            .publishOn(Schedulers.boundedElastic())
                            .flatMap(apiItem -> processBookItem(apiItem, result))
                            .collectList()
                            // --- saveBooksInBatch 호출 및 후처리 수정 ---
                            .flatMap(booksToSaveOrUpdate ->
                                    // saveBooksInBatch 호출. 성공하면 결과(result) 반환, 실패하면 에러 전파
                                    saveBooksInBatch(booksToSaveOrUpdate, result)
                                            .onErrorResume(saveError -> {
                                                // saveBooksInBatch 에서 에러 발생 시 처리
                                                log.error("[{}] DB 저장/업데이트 중 오류 발생!", syncKey, saveError);
                                                result.setErrorOccurred(true); // 에러 플래그 설정
                                                return Mono.just(result); // 에러 표시된 결과 반환
                                            })
                            )
                            .flatMap(savedResult -> { // saveBooksInBatch 성공 또는 에러 처리 후 실행됨
                                int actualLastProcessed = currentMaxProcessedPage.get();
                                savedResult.setActualLastProcessedPage(actualLastProcessed);

                                // --- 상태 업데이트 조건 재확인 및 실행 ---
                                // 1. 이번 배치 처리 중 명시적인 에러가 없었고 (`errorOccurred`가 false)
                                // 2. 실제로 처리된 페이지 번호가 이전 기록보다 크다면
                                if (!savedResult.isErrorOccurred() && actualLastProcessed > lastProcessedPage) {
                                    log.info("[{}] 성공적으로 페이지 처리 완료 ({} -> {}). Sync 상태 업데이트 시도.",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                    // 상태 업데이트 시도
                                    return updateSyncStatus(syncKey, actualLastProcessed)
                                            .thenReturn(savedResult) // 성공 시 최종 결과 반환
                                            .onErrorResume(updateError -> { // 상태 업데이트 자체 실패 시
                                                log.error("[{}] Sync 상태 업데이트 중 오류 발생!", syncKey, updateError);
                                                savedResult.setErrorOccurred(true); // 에러 플래그 설정
                                                return Mono.just(savedResult); // 에러 표시된 결과 반환
                                            });
                                } else {
                                    // 에러가 있었거나, 신규 처리된 페이지가 없는 경우
                                    if (savedResult.isErrorOccurred()) {
                                        log.error("[{}] 이전 단계 에러로 인해 Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    } else {
                                        log.warn("[{}] 신규 처리된 페이지 없음. Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    }
                                    return Mono.just(savedResult); // 현재 결과 그대로 반환
                                }
                            });
                })
                // --- 최종 에러 처리 수정 ---
                .onErrorResume(finalError -> { // 파이프라인의 예측 못한 최종 에러 처리
                    log.error("[{}] 배치 처리 중 예측하지 못한 최종 에러 발생: {}", syncKey, finalError.getMessage(), finalError);
                    result.setErrorOccurred(true); // 최종 에러 플래그 설정
                    return Mono.just(result); // 에러 표시된 결과 반환
                });
        // .onErrorReturn(result.setErrorOccurred(true)); // 이것 대신 onErrorResume 사용
    }

    /**
     * 알라딘 API 베스트셀러 특정 페이지를 호출하는 메소드 (ItemList.aspx 사용)
     * 응답을 문자열로 받아 로깅 후, JSON 파싱 시도.
     *
     * @param categoryId 조회할 카테고리 ID
     * @param pageNumber 가져올 페이지 번호 (1부터 시작)
     * @return Mono<AladinApiResponse> API 응답 DTO (성공 시), 또는 에러 Mono (실패 시)
     */
    private Mono<AladinApiResponse> fetchAladinPage(int categoryId, int pageNumber) {
        log.debug("알라딘 베스트셀러 API 요청: CategoryId={}, Page={}", categoryId, pageNumber);

        // ItemList.aspx API에 맞는 파라미터 구성
        URI uri = UriComponentsBuilder
                .fromHttpUrl(apiListUrl) // apiUrl 이 ItemList.aspx 를 가리키는지 확인!
                .queryParam("ttbkey", apiKey)
                .queryParam("QueryType", "Bestseller") // 베스트셀러 타입 지정
                .queryParam("MaxResults", MAX_RESULTS_PER_PAGE)
                .queryParam("start", pageNumber)
                // ItemList API는 SearchTarget이 필요 없을 수 있음 (API 문서 확인)
                // 필요 없다면 아래 라인 주석 처리 또는 삭제 가능
                .queryParam("SearchTarget", "Book")
                .queryParam("output", "js") // JSON 응답 요청
                .queryParam("Version", "20131101")
                .queryParam("CategoryId", categoryId) // 카테고리 ID 지정
                .encode() // 파라미터 인코딩
                .build()
                .toUri();

        return webClient.get()
                .uri(uri)
                .retrieve()
                // 1. 응답 본문을 문자열로 받기 (로깅 및 사전 확인용)
                .bodyToMono(String.class)
                // 2. 받은 문자열 처리 (로깅 및 파싱)
                .flatMap(responseBody -> {
                    // 2-1. 실제 응답 내용 로그 출력
                    log.info("[Bestseller CatId={}] API 응답 문자열 (페이지 {}): {}", categoryId, pageNumber, responseBody);
                    try {
                        // 2-2. 응답이 JSON 형식인지 간단히 확인
                        if (responseBody != null && responseBody.trim().startsWith("{")) {
                            // 2-3. ObjectMapper를 사용하여 문자열을 DTO로 직접 변환 시도
                            // *** 사용하는 DTO 클래스 경로 확인! ***
                            AladinApiResponse responseDto = objectMapper.readValue(responseBody, com.my.bookduck.controller.response.AladinApiResponse.class);
                            // 2-4. 성공 시 DTO를 담은 Mono 반환
                            return Mono.just(responseDto);
                        } else {
                            // 2-5. 응답이 JSON 형식이 아닌 경우 (예: XML 에러)
                            log.error("[Bestseller CatId={}] API 응답이 JSON 형식이 아닙니다 (페이지 {}). 응답: {}", categoryId, pageNumber, responseBody);
                            // 에러 상황을 나타내는 Mono 반환
                            return Mono.error(new RuntimeException("API 응답이 JSON 형식이 아님: " + responseBody));
                        }
                    } catch (Exception e) {
                        // 2-6. JSON 파싱 중 예외 발생 시
                        log.error("[Bestseller CatId={}] API 응답 JSON 파싱 실패 (페이지 {}): {}", categoryId, pageNumber, e.getMessage());
                        // 파싱 실패를 나타내는 에러 Mono 반환 (원본 예외 포함)
                        return Mono.error(new RuntimeException("API 응답 JSON 파싱 실패", e));
                    }
                })
                // 3. 타임아웃 설정
                .timeout(API_TIMEOUT)
                // 4. 최종 에러 로깅 (이미 처리된 파싱/형식 에러는 제외)
                .doOnError(error -> {
                    // flatMap 내부에서 이미 처리된 파싱/형식 오류는 중복 로깅 방지
                    if (!(error instanceof RuntimeException && (error.getMessage().contains("파싱 실패") || error.getMessage().contains("JSON 형식이 아님")))) {
                        log.error("알라딘 베스트셀러 API 페이지 {} (CatId={}) 호출 또는 기타 처리 오류: {}", pageNumber, categoryId, error.getMessage());
                    }
                });
    }


    /**
     * API 응답 유효성 검사 및 로깅 (에러 코드 확인 등)
     * @param response API 응답 DTO
     * @param context 로깅 컨텍스트 문자열 (예: SyncKey)
     * @param pageNumber 현재 페이지 번호
     * @return boolean 응답이 유효하면 true (처리 계속), 아니면 false
     */
    private boolean isResponseValid(AladinApiResponse response, String context, int pageNumber) {
        if (response == null) {
            log.warn("[{}] 알라딘 API 페이지 {} 응답이 null입니다.", context, pageNumber);
            return false; // 응답 자체가 null이면 유효하지 않음
        }
        // 아이템 리스트가 null 인 경우 (정상 응답이지만 아이템이 없는 경우)
        if (response.getItem() == null) {
            log.debug("[{}] 알라딘 API 페이지 {} 응답에 item 필드가 null입니다. (totalResults={})", context, pageNumber, response.getTotalResults());
            // item이 null 이어도 에러 코드가 없다면 유효한 응답으로 간주하고 처리 계속 (빈 리스트로 처리됨)
        } else if (response.getItem().isEmpty()) {
            log.debug("[{}] 알라딘 API 페이지 {} 응답의 item 리스트가 비어있습니다. (totalResults={})", context, pageNumber, response.getTotalResults());
            // 비어있는 리스트도 유효한 응답
        }
        return true; // 위 조건들에 걸리지 않으면 유효한 응답으로 판단
    }


    /**
     * AladinBookItem DTO가 유효한지 (ISBN13 존재 및 형식 확인)
     * @param item 개별 책 아이템 DTO
     * @return boolean 유효하면 true
     */
    private boolean isValidBookItem(AladinBookItem item) {
        if (item == null || item.getIsbn13() == null || item.getIsbn13().trim().isEmpty() || item.getIsbn13().length() != 13) {
            log.warn("부적합한 아이템 건너<0xEB><0x9A><0x81>니다 (ISBN13 없음 또는 형식 오류): title='{}', isbn13='{}'",
                    (item != null ? item.getTitle() : "null item"), (item != null ? item.getIsbn13() : "null item"));
            return false;
        }
        // 필요시 다른 필수 필드 검사 추가 (예: title)
        // if (item.getTitle() == null || item.getTitle().trim().isEmpty()) return false;
        return true;
    }


    /**
     * 개별 AladinBookItem을 DB와 비교하여 처리하고, 저장/업데이트가 필요한 Book 엔티티를 반환.
     * @param apiItem 알라딘 API에서 받은 책 정보
     * @param result 동기화 결과 카운팅 객체 (savedCount, updatedCount 업데이트용)
     * @return Mono<Book> 저장/업데이트 대상 엔티티, 또는 Mono.empty() 처리 불필요 시
     */
    private Mono<Book> processBookItem(AladinBookItem apiItem, SyncResult result) {
        // findByIsbn13은 블로킹 I/O 이므로 별도 스레드에서 실행
        return Mono.fromCallable(() -> bookRepository.findByIsbn13(apiItem.getIsbn13()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existingBookOpt -> { // Optional<Book> 처리
                    if (existingBookOpt.isPresent()) {
                        // --- 기존 책 업데이트 로직 ---
                        Book existingBook = existingBookOpt.get();
                        boolean updated = updateBookIfNeeded(existingBook, apiItem); // 필드 비교 및 업데이트
                        if (updated) {
                            log.debug("기존 책 정보 업데이트 대상: ISBN={}", apiItem.getIsbn13());
                            result.incrementUpdatedCount(); // 업데이트 카운트 증가
                            return Mono.just(existingBook); // 업데이트된 엔티티 반환 (saveAll 대상)
                        } else {
                            log.trace("기존 책 정보 변경 없음: ISBN={}", apiItem.getIsbn13());
                            return Mono.empty(); // 변경 없으면 저장/업데이트 대상 아님
                        }
                    } else {
                        // --- 새 책 저장 로직 ---
                        Book newBook = convertToBookEntity(apiItem); // DTO -> Entity 변환
                        if (newBook != null) {
                            log.debug("새로운 책 저장 대상: ISBN={}", apiItem.getIsbn13());
                            result.incrementSavedCount(); // 저장 카운트 증가
                            return Mono.just(newBook); // 새 엔티티 반환 (saveAll 대상)
                        } else {
                            // convertToBookEntity 에서 null 반환 (변환 실패)
                            return Mono.empty(); // 저장 대상 아님
                        }
                    }
                });
    }


    /**
     * 기존 Book 엔티티와 API 데이터를 비교하여 필요한 필드를 업데이트.
     * @param existingBook DB에 저장된 Book 엔티티
     * @param apiItem 알라딘 API에서 받은 정보
     * @return boolean 정보가 업데이트되었으면 true, 아니면 false
     */
    private boolean updateBookIfNeeded(Book existingBook, AladinBookItem apiItem) {
        boolean changed = false;
        // null 안전 비교 및 업데이트
        if (!Objects.equals(existingBook.getTitle(), apiItem.getTitle())) {
            existingBook.setTitle(apiItem.getTitle());
            changed = true;
        }
        if (!Objects.equals(existingBook.getCover(), apiItem.getCover())) {
            existingBook.setCover(apiItem.getCover());
            changed = true;
        }
        if (!Objects.equals(existingBook.getWriter(), apiItem.getAuthor())) {
            existingBook.setWriter(apiItem.getAuthor());
            changed = true;
        }
        if (!Objects.equals(existingBook.getPublishing(), apiItem.getPublisher())) {
            existingBook.setPublishing(apiItem.getPublisher());
            changed = true;
        }
        if (existingBook.getPrice() != apiItem.getPriceStandard()) { // int 비교
            existingBook.setPrice(apiItem.getPriceStandard());
            changed = true;
        }
        LocalDate apiPubDate = Book.parseDate(apiItem.getPubDate()); // static 메소드 사용
        // API 에서 받은 날짜가 유효하고, 기존 날짜와 다를 경우 업데이트
        if (apiPubDate != null && !Objects.equals(existingBook.getPublicationDate(), apiPubDate)) {
            existingBook.setPublicationDate(apiPubDate);
            changed = true;
        }
        // TODO: 필요에 따라 다른 필드 (설명, 카테고리 등) 비교 및 업데이트 로직 추가

        return changed;
    }


    /**
     * AladinBookItem DTO를 Book 엔티티로 변환. 실패 시 null 반환.
     * @param item 알라딘 책 아이템 DTO
     * @return Book 엔티티 또는 null
     */
    private Book convertToBookEntity(AladinBookItem item) {
        try {
            LocalDate publicationDate = Book.parseDate(item.getPubDate()); // static 메소드 활용

            return Book.aladdinBuilder() // 엔티티의 빌더 사용 (이름 확인 필요)
                    .title(item.getTitle())
                    .cover(item.getCover())
                    .writer(item.getAuthor())
                    .publicationDate(publicationDate)
                    .publishing(item.getPublisher())
                    .price(item.getPriceStandard()) // 필요시 priceSales 등 다른 가격 사용
                    .isbn13(item.getIsbn13())
                    // identifier, epubPath 등 API에서 오지 않는 필드는 null 또는 기본값
                    .buildFromAladdin(); // 빌더의 build 메소드 이름 확인
        } catch (Exception e) {
            log.error("Book 엔티티 변환 중 오류 발생: isbn13={}, title={}, 오류: {}",
                    item.getIsbn13(), item.getTitle(), e.getMessage(), e);
            return null; // 변환 실패 시 null 반환
        }
    }


    /**
     * 주어진 Book 엔티티 리스트를 DB에 일괄 저장/업데이트하고 결과를 반환.
     * @param booksToSaveOrUpdate 저장 또는 업데이트할 Book 엔티티 리스트
     * @param result 동기화 결과 DTO (로그 출력용)
     * @return Mono<SyncResult> 작업 완료 후 결과 DTO
     */
    // @Transactional // 단일 saveAll 호출이므로 여기에 트랜잭션 걸 수 있음
    private Mono<SyncResult> saveBooksInBatch(List<Book> booksToSaveOrUpdate, SyncResult result) {
        if (booksToSaveOrUpdate == null || booksToSaveOrUpdate.isEmpty()) {
            log.info("DB에 저장하거나 업데이트할 책이 없습니다.");
            // 변경사항이 없어도 결과 DTO는 반환해야 함
            return Mono.just(result);
        }
        // saveAll은 블로킹 메소드이므로 별도 스레드에서 실행
        return Mono.fromRunnable(() -> {
                    bookRepository.saveAll(booksToSaveOrUpdate); // JPA가 ID 존재 여부로 INSERT/UPDATE 결정
                    log.info("총 {}권의 책 정보를 저장 또는 업데이트 완료 (신규: {}, 업데이트: {}).",
                            result.getSavedCount() + result.getUpdatedCount(),
                            result.getSavedCount(), result.getUpdatedCount());
                })
                .subscribeOn(Schedulers.boundedElastic()) // DB 작업 스레드 지정
                .thenReturn(result); // 작업 완료 후 업데이트된 결과 객체 반환
    }

    /**
     * 동기화 상태 (마지막 처리 페이지)를 DB에 저장 또는 업데이트 (블로킹)
     * @param syncKey 상태 구분 키 (예: "bestseller_0")
     * @param lastProcessedPage 저장할 마지막 처리 페이지 번호
     * @return Mono<Void> 작업 완료 신호
     */
    // @Transactional // 단일 엔티티 저장/업데이트이므로 필요시 적용 가능
    private Mono<Void> updateSyncStatus(String syncKey, int lastProcessedPage) {
        // DB 작업은 블로킹이므로 별도 스레드에서 실행
        return Mono.fromRunnable(() -> {
                    // 키로 기존 상태 조회, 없으면 새로 생성
                    SyncStatus status = syncStatusRepository.findById(syncKey)
                            .orElse(new SyncStatus(syncKey, 0)); // 초기 상태
                    status.setLastProcessedPage(lastProcessedPage); // 페이지 번호 업데이트
                    status.setLastUpdated(LocalDateTime.now()); // 업데이트 시간 기록
                    syncStatusRepository.save(status); // DB에 저장 (INSERT or UPDATE)
                    log.info("Sync 상태 업데이트 완료: Key={}, LastProcessedPage={}", syncKey, lastProcessedPage);
                })
                .subscribeOn(Schedulers.boundedElastic()) // 스레드 지정
                .then(); // 작업 완료 신호만 반환 (결과값 없음)
    }
}
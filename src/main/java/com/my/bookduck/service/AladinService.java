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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
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

    @Value("${aladin.api.key}")
    private String apiKey;

    @Value("${aladin.api.url}")
    private String apiUrl;

    @Value("${aladin.api.itemlist}")
    private String apiListUrl;

    private final RestTemplate restTemplate;
    private final BookRepository bookRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SyncStatusRepository syncStatusRepository;

    private static final int MAX_RESULTS_PER_PAGE = 50;
    private static final int PAGES_PER_RUN = 20;
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_BESTSELLER_CATEGORY_ID = 0;

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class SyncResult {
        private long totalApiItems = 0;
        private long savedCount = 0;
        private long updatedCount = 0;
        private boolean errorOccurred = false;
        private int startPage = 1;
        private int lastAttemptedPage = 0;
        private int actualLastProcessedPage = 0;

        public void incrementSavedCount() { this.savedCount++; }
        public void incrementUpdatedCount() { this.updatedCount++; }
        public SyncResult setErrorOccurred(boolean errorOccurred) { this.errorOccurred = errorOccurred; return this; }
    }

    /**
     * 알라딘 API를 통해 책을 검색하거나 목록을 조회합니다.
     * 검색어가 있으면 키워드 검색, 없으면 정렬 기준에 따라 신간 또는 베스트셀러 목록을 반환합니다.
     */
    public PaginatedAladinResponse searchBooks(String query, int page, int size, String categoryId, String sort) {
        String effectiveSort = sort != null && List.of("PublishTime", "SalesPoint", "Title").contains(sort)
                ? sort
                : "PublishTime"; // 기본 정렬은 최신순
        String effectiveCategoryId = (categoryId != null && !categoryId.trim().isEmpty())
                ? categoryId
                : "0"; // 알라딘 API에서 0은 보통 전체 국내도서를 의미 (문서 확인 필요)

        UriComponentsBuilder uriBuilder;
        String finalApiUrl; // 사용할 알라딘 API의 기본 URL (ItemSearch.aspx 또는 ItemList.aspx)

        // --- 검색어 유무 및 정렬 기준에 따른 API 분기 ---
        if (query == null || query.trim().isEmpty()) {
            // 검색어가 없을 때
            finalApiUrl = apiListUrl; // ItemList.aspx 사용
            uriBuilder = UriComponentsBuilder.fromUriString(finalApiUrl)
                    .queryParam("TTBKey", apiKey)
                    .queryParam("MaxResults", size)
                    .queryParam("start", page)
                    .queryParam("SearchTarget", "eBook") // 또는 "eBook" (프로젝트 대상에 맞게)
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .queryParam("CategoryId", effectiveCategoryId);

            if ("PublishTime".equals(effectiveSort)) {
                log.info("검색어 없음 + 최신순 정렬: Aladin 신간 목록 API 호출 (ItemNewSpecial)");
                uriBuilder.queryParam("QueryType", "ItemNewSpecial");
                // ItemList.aspx의 ItemNewAll은 자체적으로 최신순이므로 별도 Sort 파라미터 필요 없을 수 있음
                // 필요하다면 알라딘 문서 확인 후 Sort 파라미터 추가
            } else if ("SalesPoint".equals(effectiveSort)) {
                log.info("검색어 없음 + 판매량순 정렬: Aladin 베스트셀러 API 호출 (Bestseller)");
                uriBuilder.queryParam("QueryType", "Bestseller");
                // ItemList.aspx의 Bestseller는 자체적으로 판매량순이므로 별도 Sort 파라미터 필요 없을 수 있음
            } else if ("Title".equals(effectiveSort)) {
                log.info("검색어 없음 + 가나다순 정렬: 처리 방식 결정 필요");
                uriBuilder.queryParam("QueryType", "ItemNewSpecial");
                // ★★★ 알라딘 ItemList API가 'Title' 정렬을 지원하는지 확인 필요 ★★★
                // 1. 지원한다면:
                // uriBuilder.queryParam("QueryType", "ItemNewAll"); // 또는 다른 적절한 QueryType
                // uriBuilder.queryParam("Sort", "Title"); // 알라딘 API의 실제 파라미터명 사용
                // 2. 지원하지 않는다면:
                //  - 기본 정렬(예: 최신순)으로 대체하고 로그 남기기
                //  - 또는 빈 결과를 반환하고 프론트에서 안내 메시지 표시
                // 여기서는 예시로 신간 목록(기본 최신순)을 반환하거나,
                // 특정 QueryType(예: 그냥 ItemList)에서 Title 정렬이 가능하다면 해당 옵션 사용
                uriBuilder.queryParam("QueryType", "ItemNewAll"); // 예시: 신간을 대상으로 가나다순 시도 (API 지원 여부 확인)
                // uriBuilder.queryParam("Sort", "Title"); // API가 지원한다면
                log.warn("검색어 없이 가나다순 정렬 시, Aladin ItemList API의 Title 정렬 지원 여부에 따라 결과가 달라질 수 있습니다.");
            } else {
                // 기타 정렬 또는 기본값 (최신순)
                log.info("검색어 없음 + 기본/기타 정렬: Aladin 신간 목록 API 호출 (ItemNewAll)");
                uriBuilder.queryParam("QueryType", "ItemNewAll");
            }
        } else {
            // 검색어가 있을 때 (기존 로직 유지)
            finalApiUrl = apiUrl; // ItemSearch.aspx 사용
            log.info("검색어 있음: Aladin 키워드 검색 API 호출. Query: {}, Sort: {}", query, effectiveSort);
            uriBuilder = UriComponentsBuilder.fromUriString(finalApiUrl)
                    .queryParam("TTBKey", apiKey)
                    .queryParam("Query", query)
                    .queryParam("QueryType", "Keyword")
                    .queryParam("MaxResults", size)
                    .queryParam("start", page)
                    .queryParam("SearchTarget", "eBook") // 또는 "eBook"
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .queryParam("CategoryId", effectiveCategoryId)
                    .queryParam("Sort", effectiveSort); // 키워드 검색은 Sort 파라미터 지원
        }

        URI uri = uriBuilder.encode(StandardCharsets.UTF_8).build().toUri();
        log.info("최종 호출 Aladin API URL: {}", uri.toString());

        try {
            AladinApiResponse response = restTemplate.getForObject(uri, AladinApiResponse.class);
            if (response != null) {
                List<AladinBookItem> books = (response.getItem() != null) ? response.getItem() : Collections.emptyList();
                int total = response.getTotalResults() != null ? response.getTotalResults() : 0;
                // 검색어 없는 ItemList API의 경우 totalResults가 정확하지 않거나 매우 클 수 있음
                // 프론트엔드 페이지네이션과 사용자 경험을 위해 조정 필요할 수 있음
                if ((query == null || query.trim().isEmpty()) && total == 0 && !books.isEmpty()) {
                    // 예: ItemList API가 totalResults를 0으로 주지만 실제론 더 많은 결과가 있을 수 있는 경우
                    // total = books.size() * 20; // 임의로 더 많은 결과가 있다고 가정 (페이지네이션 테스트용)
                    log.warn("검색어 없는 목록 조회 시 API totalResults가 0이지만, 아이템이 존재합니다. 페이지네이션에 영향이 있을 수 있습니다.");
                }
                log.info("Aladin API 응답: totalResults={}, 현재 페이지 아이템 수={}", total, books.size());
                return new PaginatedAladinResponse(books, total, page, size);
            } else {
                log.warn("알라딘 API 응답이 null입니다. URL: {}", uri);
                return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
            }
        } catch (Exception e) {
            log.error("알라딘 API 호출 중 오류 발생. URL: {}. Error: {}", uri, e.getMessage(), e);
            return new PaginatedAladinResponse(Collections.emptyList(), 0, page, size);
        }
    }

//    public PaginatedAladinResponse getBestsellers(int page, int size, String categoryId, String sort) {
//        String effectiveSort = sort != null && List.of("PublishTime", "SalesPoint", "Title").contains(sort)
//                ? sort
//                : "PublishTime";
//        String effectiveCategoryId = (categoryId != null && !categoryId.trim().isEmpty())
//                ? categoryId
//                : String.valueOf(DEFAULT_BESTSELLER_CATEGORY_ID);
//
//        return fetchAladinPage(Integer.parseInt(effectiveCategoryId), page)
//                .map(response -> {
//                    List<AladinBookItem> books = (response.getItem() != null) ? response.getItem() : Collections.emptyList();
//                    Integer totalResults = response.getTotalResults();
//                    int total = totalResults != null ? totalResults : books.size();
//                    books = books.size() > size ? books.subList(0, size) : books;
//                    return new PaginatedAladinResponse(books, total, page, size);
//                })
//                .block();
//    }

    private Mono<AladinApiResponse> fetchAladinPage(int categoryId, int pageNumber) {
        log.debug("알라딘 베스트셀러 API 요청: CategoryId={}, Page={}", categoryId, pageNumber);

        URI uri = UriComponentsBuilder
                .fromHttpUrl(apiListUrl)
                .queryParam("ttbkey", apiKey)
                .queryParam("QueryType", "Bestseller")
                .queryParam("MaxResults", MAX_RESULTS_PER_PAGE)
                .queryParam("start", pageNumber)
                .queryParam("SearchTarget", "eBook") // eBook -> Book
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .queryParam("CategoryId", categoryId)
                .encode()
                .build()
                .toUri();

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    log.info("[Bestseller CatId={}] API 응답 문자열 (페이지 {}): {}", categoryId, pageNumber, responseBody);
                    try {
                        if (responseBody != null && responseBody.trim().startsWith("{")) {
                            AladinApiResponse responseDto = objectMapper.readValue(responseBody, com.my.bookduck.controller.response.AladinApiResponse.class);
                            return Mono.just(responseDto);
                        } else {
                            log.error("[Bestseller CatId={}] API 응답이 JSON 형식이 아닙니다 (페이지 {}). 응답: {}", categoryId, pageNumber, responseBody);
                            return Mono.error(new RuntimeException("API 응답이 JSON 형식이 아님: " + responseBody));
                        }
                    } catch (Exception e) {
                        log.error("[Bestseller CatId={}] API 응답 JSON 파싱 실패 (페이지 {}): {}", categoryId, pageNumber, e.getMessage());
                        return Mono.error(new RuntimeException("API 응답 JSON 파싱 실패", e));
                    }
                })
                .timeout(API_TIMEOUT)
                .doOnError(error -> {
                    if (!(error instanceof RuntimeException && (error.getMessage().contains("파싱 실패") || error.getMessage().contains("JSON 형식이 아님")))) {
                        log.error("알라딘 베스트셀러 API 페이지 {} (CatId={}) 호출 또는 기타 처리 오류: {}", pageNumber, categoryId, error.getMessage());
                    }
                });
    }

    public Mono<SyncResult> fetchNextBestsellerBatch(int categoryId) {
        String syncKey = "bestseller_" + categoryId;
        log.info("알라딘 API 다음 베스트셀러 배치(SyncKey={}) 처리 시작", syncKey);
        SyncResult result = new SyncResult();

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

                    return Flux.range(startPage, pagesToFetchThisRun)
                            .concatMap(pageNumber -> fetchAladinPage(categoryId, pageNumber)
                                            .doOnSuccess(response -> {
                                                if (isResponseValid(response, syncKey, pageNumber)) {
                                                    currentMaxProcessedPage.accumulateAndGet(pageNumber, Math::max);
                                                    if (response.getItem() != null && !response.getItem().isEmpty()) {
                                                        result.setTotalApiItems(result.getTotalApiItems() + response.getItem().size());
                                                    }
                                                }
                                            })
                                            .flatMapMany(pageResponse -> Flux.fromIterable(
                                                    (pageResponse != null && pageResponse.getItem() != null) ? pageResponse.getItem() : Collections.emptyList()
                                            ))
                                            .onErrorResume(e -> {
                                                log.warn("[{}] 페이지 {} 처리 중 오류 발생하여 건너뛰니다: {}", syncKey, pageNumber, e.getMessage());
                                                return Flux.empty();
                                            }),
                                    1)
                            .filter(this::isValidBookItem)
                            .publishOn(Schedulers.boundedElastic())
                            .flatMap(apiItem -> processBookItem(apiItem, result))
                            .collectList()
                            .flatMap(booksToSaveOrUpdate ->
                                    saveBooksInBatch(booksToSaveOrUpdate, result)
                                            .onErrorResume(saveError -> {
                                                log.error("[{}] DB 저장/업데이트 중 오류 발생!", syncKey, saveError);
                                                result.setErrorOccurred(true);
                                                return Mono.just(result);
                                            })
                            )
                            .flatMap(savedResult -> {
                                int actualLastProcessed = currentMaxProcessedPage.get();
                                savedResult.setActualLastProcessedPage(actualLastProcessed);

                                if (!savedResult.isErrorOccurred() && actualLastProcessed > lastProcessedPage) {
                                    log.info("[{}] 성공적으로 페이지 처리 완료 ({} -> {}). Sync 상태 업데이트 시도.",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                    return updateSyncStatus(syncKey, actualLastProcessed)
                                            .thenReturn(savedResult)
                                            .onErrorResume(updateError -> {
                                                log.error("[{}] Sync 상태 업데이트 중 오류 발생!", syncKey, updateError);
                                                savedResult.setErrorOccurred(true);
                                                return Mono.just(savedResult);
                                            });
                                } else {
                                    if (savedResult.isErrorOccurred()) {
                                        log.error("[{}] 이전 단계 에러로 인해 Sync 상태 업데이트 건너뛰기 (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    } else {
                                        log.warn("[{}] 신규 처리된 페이지 없음. Sync 상태 업데이트 건너뛰기 (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    }
                                    return Mono.just(savedResult);
                                }
                            });
                })
                .onErrorResume(finalError -> {
                    log.error("[{}] 배치 처리 중 예측하지 못한 최종 에러 발생: {}", syncKey, finalError.getMessage(), finalError);
                    result.setErrorOccurred(true);
                    return Mono.just(result);
                });
    }

    private boolean isResponseValid(AladinApiResponse response, String context, int pageNumber) {
        if (response == null) {
            log.warn("[{}] 알라딘 API 페이지 {} 응답이 null입니다.", context, pageNumber);
            return false;
        }
        if (response.getItem() == null) {
            log.debug("[{}] 알라딘 API 페이지 {} 응답에 item 필드가 null입니다. (totalResults={})", context, pageNumber, response.getTotalResults());
        } else if (response.getItem().isEmpty()) {
            log.debug("[{}] 알라딘 API 페이지 {} 응답의 item 리스트가 비어있습니다. (totalResults={})", context, pageNumber, response.getTotalResults());
        }
        return true;
    }

    private boolean isValidBookItem(AladinBookItem item) {
        if (item == null || item.getIsbn13() == null) {
            log.warn("부적합한 아이템 건너뛰니다 (ISBN13 없음 또는 형식 오류): title='{}', isbn13='{}'",
                    (item != null ? item.getTitle() : "null item"), (item != null ? item.getIsbn13() : "null item"));
            return false;
        }
        return true;
    }

    private Mono<Book> processBookItem(AladinBookItem apiItem, SyncResult result) {
        return Mono.fromCallable(() -> bookRepository.findById(apiItem.getIsbn13()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existingBookOpt -> {
                    if (existingBookOpt.isPresent()) {
                        Book existingBook = existingBookOpt.get();
                        boolean updated = updateBookIfNeeded(existingBook, apiItem);
                        if (updated) {
                            log.debug("기존 책 정보 업데이트 대상: ISBN={}", apiItem.getIsbn13());
                            result.incrementUpdatedCount();
                            return Mono.just(existingBook);
                        } else {
                            log.trace("기존 책 정보 변경 없음: ISBN={}", apiItem.getIsbn13());
                            return Mono.empty();
                        }
                    } else {
                        Book newBook = convertToBookEntity(apiItem);
                        if (newBook != null) {
                            log.debug("새로운 책 저장 대상: ISBN={}", apiItem.getIsbn13());
                            result.incrementSavedCount();
                            return Mono.just(newBook);
                        } else {
                            return Mono.empty();
                        }
                    }
                });
    }

    private boolean updateBookIfNeeded(Book existingBook, AladinBookItem apiItem) {
        boolean changed = false;
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
        if (existingBook.getPrice() != apiItem.getPriceStandard()) {
            existingBook.setPrice(apiItem.getPriceStandard());
            changed = true;
        }
        LocalDate apiPubDate = Book.parseDate(apiItem.getPubDate());
        if (apiPubDate != null && !Objects.equals(existingBook.getPublicationDate(), apiPubDate)) {
            existingBook.setPublicationDate(apiPubDate);
            changed = true;
        }
        return changed;
    }

    private Book convertToBookEntity(AladinBookItem item) {
        try {
            LocalDate publicationDate = Book.parseDate(item.getPubDate());
            return Book.aladdinBuilder()
                    .title(item.getTitle())
                    .cover(item.getCover())
                    .writer(item.getAuthor())
                    .publicationDate(publicationDate)
                    .publishing(item.getPublisher())
                    .price(item.getPriceStandard())
                    .isbn13(item.getIsbn13())
                    .buildFromAladdin();
        } catch (Exception e) {
            log.error("Book 엔티티 변환 중 오류 발생: isbn13={}, title={}, 오류: {}",
                    item.getIsbn13(), item.getTitle(), e.getMessage(), e);
            return null;
        }
    }

    private Mono<SyncResult> saveBooksInBatch(List<Book> booksToSaveOrUpdate, SyncResult result) {
        if (booksToSaveOrUpdate == null || booksToSaveOrUpdate.isEmpty()) {
            log.info("DB에 저장하거나 업데이트할 책이 없습니다.");
            return Mono.just(result);
        }
        return Mono.fromRunnable(() -> {
                    bookRepository.saveAll(booksToSaveOrUpdate);
                    log.info("총 {}권의 책 정보를 저장 또는 업데이트 완료 (신규: {}, 업데이트: {}).",
                            result.getSavedCount() + result.getUpdatedCount(),
                            result.getSavedCount(), result.getUpdatedCount());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(result);
    }

    private Mono<Void> updateSyncStatus(String syncKey, int lastProcessedPage) {
        return Mono.fromRunnable(() -> {
                    SyncStatus status = syncStatusRepository.findById(syncKey)
                            .orElse(new SyncStatus(syncKey, 0));
                    status.setLastProcessedPage(lastProcessedPage);
                    status.setLastUpdated(LocalDateTime.now());
                    syncStatusRepository.save(status);
                    log.info("Sync 상태 업데이트 완료: Key={}, LastProcessedPage={}", syncKey, lastProcessedPage);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
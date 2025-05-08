package com.my.bookduck.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.bookduck.controller.response.AladinApiResponse;
import com.my.bookduck.controller.response.AladinBookItem; // ★수정★: 이 DTO의 isbn13이 Long 타입임
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
    private String apiUrl; // 상품 검색 API용 (ItemSearch.aspx)

    @Value("${aladin.api.itemlist}")
    private String apiListUrl; // 상품 리스트 API용 (ItemList.aspx) - 베스트셀러용

    private final RestTemplate restTemplate;
    private final BookRepository bookRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SyncStatusRepository syncStatusRepository;

    private static final int MAX_RESULTS_PER_PAGE = 50;
    private static final int PAGES_PER_RUN = 20;
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);
    public static final int DEFAULT_BESTSELLER_CATEGORY_ID = 0;
    public static final int DEFAULT_EBOOK_BESTSELLER_CATEGORY_ID = 0;

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

    public Mono<SyncResult> fetchNextBestsellerBatch(int categoryId) {
        String syncKey = "bestseller_" + categoryId;
        log.info("알라딘 API 다음 일반 도서 베스트셀러 배치(SyncKey={}) 처리 시작", syncKey);
        SyncResult result = new SyncResult();

        return Mono.fromCallable(() -> syncStatusRepository.findById(syncKey)
                        .map(SyncStatus::getLastProcessedPage)
                        .orElse(0))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(lastProcessedPage -> {
                    int startPage = lastProcessedPage + 1;
                    result.setStartPage(startPage);
                    int pagesToFetchThisRun = PAGES_PER_RUN;
                    int maxApiPageLimit = (200 / MAX_RESULTS_PER_PAGE);

                    if (startPage > maxApiPageLimit) {
                        log.warn("[{}] 시작 페이지({})가 알라딘 API 최대 페이지({})를 초과하여 더 이상 가져올 일반 도서 데이터가 없습니다.", syncKey, startPage, maxApiPageLimit);
                        result.setLastAttemptedPage(startPage -1);
                        result.setActualLastProcessedPage(lastProcessedPage);
                        return Mono.just(result);
                    }
                    if (startPage + pagesToFetchThisRun -1 > maxApiPageLimit) {
                        pagesToFetchThisRun = maxApiPageLimit - startPage + 1;
                    }

                    result.setLastAttemptedPage(startPage + pagesToFetchThisRun - 1);
                    log.info("[{}] DB 조회 결과 lastProcessedPage={}, 이번 실행 시작 페이지={}, 가져올 페이지 수={}",
                            syncKey, lastProcessedPage, startPage, pagesToFetchThisRun);
                    AtomicInteger currentMaxProcessedPage = new AtomicInteger(lastProcessedPage);

                    return Flux.range(startPage, pagesToFetchThisRun)
                            .concatMap(pageNumber -> fetchAladinBestsellerPage(categoryId, pageNumber, "Book")
                                            .doOnSuccess(response -> {
                                                if (isResponseValid(response, syncKey, pageNumber)) {
                                                    if (response.getItem() != null && !response.getItem().isEmpty()) {
                                                        currentMaxProcessedPage.accumulateAndGet(pageNumber, Math::max);
                                                        result.setTotalApiItems(result.getTotalApiItems() + response.getItem().size());
                                                    } else {
                                                        log.info("[{}] 페이지 {}에 더 이상 일반 도서 아이템이 없습니다.", syncKey, pageNumber);
                                                    }
                                                }
                                            })
                                            .flatMapMany(pageResponse -> Flux.fromIterable(
                                                    (pageResponse != null && pageResponse.getItem() != null) ? pageResponse.getItem() : Collections.<AladinBookItem>emptyList()
                                            ))
                                            .onErrorResume(e -> {
                                                log.warn("[{}] 일반 도서 페이지 {} 처리 중 오류 발생하여 건너<0xEB><0x9A><0x81>니다: {}", syncKey, pageNumber, e.getMessage());
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
                                                log.error("[{}] 일반 도서 DB 저장/업데이트 중 오류 발생!", syncKey, saveError);
                                                result.setErrorOccurred(true);
                                                return Mono.just(result);
                                            })
                            )
                            .flatMap(savedResult -> {
                                int actualLastProcessed = currentMaxProcessedPage.get();
                                if (savedResult.getTotalApiItems() == 0 && actualLastProcessed == lastProcessedPage && startPage > lastProcessedPage) {
                                    log.info("[{}] API에서 가져온 일반 도서 아이템이 없어 실제 처리된 페이지 번호 변경 없음 ({} -> {}).",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                }
                                savedResult.setActualLastProcessedPage(actualLastProcessed);

                                if (!savedResult.isErrorOccurred() && actualLastProcessed > lastProcessedPage) {
                                    log.info("[{}] 성공적으로 일반 도서 페이지 처리 완료 ({} -> {}). Sync 상태 업데이트 시도.",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                    return updateSyncStatus(syncKey, actualLastProcessed)
                                            .thenReturn(savedResult)
                                            .onErrorResume(updateError -> {
                                                log.error("[{}] 일반 도서 Sync 상태 업데이트 중 오류 발생!", syncKey, updateError);
                                                savedResult.setErrorOccurred(true);
                                                return Mono.just(savedResult);
                                            });
                                } else {
                                    if (savedResult.isErrorOccurred()) {
                                        log.error("[{}] 이전 단계 에러로 인해 일반 도서 Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    } else {
                                        log.warn("[{}] 신규 처리된 일반 도서 페이지 없음. Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    }
                                    return Mono.just(savedResult);
                                }
                            });
                })
                .onErrorResume(finalError -> {
                    log.error("[{}] 일반 도서 배치 처리 중 예측하지 못한 최종 에러 발생: {}", syncKey, finalError.getMessage(), finalError);
                    result.setErrorOccurred(true);
                    return Mono.just(result);
                });
    }

    public Mono<SyncResult> fetchNextEBookBestsellerBatch(int categoryId) {
        String syncKey = "ebook_bestseller_" + categoryId;
        log.info("알라딘 API 다음 eBook 베스트셀러 배치(SyncKey={}) 처리 시작", syncKey);
        SyncResult result = new SyncResult();

        return Mono.fromCallable(() -> syncStatusRepository.findById(syncKey)
                        .map(SyncStatus::getLastProcessedPage)
                        .orElse(0))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(lastProcessedPage -> {
                    int startPage = lastProcessedPage + 1;
                    result.setStartPage(startPage);
                    int pagesToFetchThisRun = PAGES_PER_RUN;
                    int maxApiPageLimit = (200 / MAX_RESULTS_PER_PAGE);

                    if (startPage > maxApiPageLimit) {
                        log.warn("[{}] 시작 페이지({})가 알라딘 API 최대 페이지({})를 초과하여 더 이상 가져올 eBook 데이터가 없습니다.", syncKey, startPage, maxApiPageLimit);
                        result.setLastAttemptedPage(startPage -1);
                        result.setActualLastProcessedPage(lastProcessedPage);
                        return Mono.just(result);
                    }
                    if (startPage + pagesToFetchThisRun -1 > maxApiPageLimit) {
                        pagesToFetchThisRun = maxApiPageLimit - startPage + 1;
                    }

                    result.setLastAttemptedPage(startPage + pagesToFetchThisRun - 1);
                    log.info("[{}] DB 조회 결과 lastProcessedPage={}, 이번 실행 시작 페이지={}, 가져올 페이지 수={}",
                            syncKey, lastProcessedPage, startPage, pagesToFetchThisRun);
                    AtomicInteger currentMaxProcessedPage = new AtomicInteger(lastProcessedPage);

                    return Flux.range(startPage, pagesToFetchThisRun)
                            .concatMap(pageNumber -> fetchAladinBestsellerPage(categoryId, pageNumber, "eBook")
                                            .doOnSuccess(response -> {
                                                if (isResponseValid(response, syncKey, pageNumber)) {
                                                    if (response.getItem() != null && !response.getItem().isEmpty()) {
                                                        currentMaxProcessedPage.accumulateAndGet(pageNumber, Math::max);
                                                        result.setTotalApiItems(result.getTotalApiItems() + response.getItem().size());
                                                    } else {
                                                        log.info("[{}] 페이지 {}에 더 이상 eBook 아이템이 없습니다.", syncKey, pageNumber);
                                                    }
                                                }
                                            })
                                            .flatMapMany(pageResponse -> Flux.fromIterable(
                                                    (pageResponse != null && pageResponse.getItem() != null) ? pageResponse.getItem() : Collections.<AladinBookItem>emptyList()
                                            ))
                                            .onErrorResume(e -> {
                                                log.warn("[{}] eBook 페이지 {} 처리 중 오류 발생하여 건너<0xEB><0x9A><0x81>니다: {}", syncKey, pageNumber, e.getMessage());
                                                return Flux.empty();
                                            }),
                                    1)
                            .filter(this::isValidEBookItem)
                            .publishOn(Schedulers.boundedElastic())
                            .flatMap(apiItem -> processBookItem(apiItem, result))
                            .collectList()
                            .flatMap(booksToSaveOrUpdate ->
                                    saveBooksInBatch(booksToSaveOrUpdate, result)
                                            .onErrorResume(saveError -> {
                                                log.error("[{}] eBook DB 저장/업데이트 중 오류 발생!", syncKey, saveError);
                                                result.setErrorOccurred(true);
                                                return Mono.just(result);
                                            })
                            )
                            .flatMap(savedResult -> {
                                int actualLastProcessed = currentMaxProcessedPage.get();
                                if (savedResult.getTotalApiItems() == 0 && actualLastProcessed == lastProcessedPage && startPage > lastProcessedPage) {
                                    log.info("[{}] API에서 가져온 eBook 아이템이 없어 실제 처리된 페이지 번호 변경 없음 ({} -> {}).",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                }
                                savedResult.setActualLastProcessedPage(actualLastProcessed);

                                if (!savedResult.isErrorOccurred() && actualLastProcessed > lastProcessedPage) {
                                    log.info("[{}] 성공적으로 eBook 페이지 처리 완료 ({} -> {}). Sync 상태 업데이트 시도.",
                                            syncKey, lastProcessedPage, actualLastProcessed);
                                    return updateSyncStatus(syncKey, actualLastProcessed)
                                            .thenReturn(savedResult)
                                            .onErrorResume(updateError -> {
                                                log.error("[{}] eBook Sync 상태 업데이트 중 오류 발생!", syncKey, updateError);
                                                savedResult.setErrorOccurred(true);
                                                return Mono.just(savedResult);
                                            });
                                } else {
                                    if (savedResult.isErrorOccurred()) {
                                        log.error("[{}] 이전 단계 에러로 인해 eBook Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    } else {
                                        log.warn("[{}] 신규 처리된 eBook 페이지 없음. Sync 상태 업데이트 건너<0xEB><0x9A><0x81> (LastProcessed: {}, CurrentMax: {})",
                                                syncKey, lastProcessedPage, actualLastProcessed);
                                    }
                                    return Mono.just(savedResult);
                                }
                            });
                })
                .onErrorResume(finalError -> {
                    log.error("[{}] eBook 배치 처리 중 예측하지 못한 최종 에러 발생: {}", syncKey, finalError.getMessage(), finalError);
                    result.setErrorOccurred(true);
                    return Mono.just(result);
                });
    }

    private Mono<AladinApiResponse> fetchAladinBestsellerPage(int categoryId, int pageNumber, String searchTarget) {
        log.debug("알라딘 베스트셀러 API 요청: CategoryId={}, Page={}, SearchTarget={}", categoryId, pageNumber, searchTarget);
        URI uri = UriComponentsBuilder
                .fromHttpUrl(apiListUrl)
                .queryParam("ttbkey", apiKey)
                .queryParam("QueryType", "Bestseller")
                .queryParam("MaxResults", MAX_RESULTS_PER_PAGE)
                .queryParam("start", pageNumber)
                .queryParam("SearchTarget", searchTarget)
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .queryParam("CategoryId", categoryId)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    log.trace("[Bestseller CatId={}, Target={}, Page={}] API 응답 문자열: {}", categoryId, searchTarget, pageNumber, responseBody);
                    try {
                        if (responseBody != null && responseBody.trim().startsWith("{")) {
                            AladinApiResponse responseDto = objectMapper.readValue(responseBody, com.my.bookduck.controller.response.AladinApiResponse.class);
                            if (responseDto.getItem() == null) {
                                responseDto.setItem(Collections.emptyList());
                            }
                            return Mono.just(responseDto);
                        } else {
                            log.error("[Bestseller CatId={}, Target={}, Page={}] API 응답이 JSON 형식이 아님. 응답: {}", categoryId, searchTarget, pageNumber, responseBody.substring(0, Math.min(responseBody.length(), 500)));
                            return Mono.error(new RuntimeException("API 응답이 JSON 형식이 아님"));
                        }
                    } catch (Exception e) {
                        log.error("[Bestseller CatId={}, Target={}, Page={}] API 응답 JSON 파싱 실패: {}, 응답 일부: {}",
                                categoryId, searchTarget, pageNumber, e.getMessage(), responseBody.substring(0, Math.min(responseBody.length(), 500)), e);
                        return Mono.error(new RuntimeException("API 응답 JSON 파싱 실패", e));
                    }
                })
                .timeout(API_TIMEOUT)
                .doOnError(error -> {
                    if (!(error instanceof RuntimeException && (error.getMessage().contains("파싱 실패") || error.getMessage().contains("JSON 형식이 아님")))) {
                        log.error("알라딘 베스트셀러 API 페이지 {} (CatId={}, Target={}) 호출 또는 네트워크 오류: {}", pageNumber, categoryId, searchTarget, error.getMessage(), error);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("fetchAladinBestsellerPage 최종 에러 처리: CatId={}, Target={}, Page={}. 빈 응답 반환. 에러: {}", categoryId, searchTarget, pageNumber, e.getMessage());
                    AladinApiResponse emptyResponse = new AladinApiResponse();
                    emptyResponse.setItem(Collections.emptyList());
                    return Mono.just(emptyResponse);
                });
    }

    private boolean isResponseValid(AladinApiResponse response, String context, int pageNumber) {
        if (response == null) {
            log.warn("[{}] 알라딘 API 페이지 {} 에 대한 처리된 응답이 null입니다.", context, pageNumber);
            return false;
        }
        return true;
    }

    private boolean isValidBookItem(AladinBookItem item) {
        // ★수정★: isbn13이 Long 타입이므로 null 체크만 수행
        if (item == null || item.getIsbn13() == null) {
            log.warn("부적합한 일반 도서 아이템 (ISBN13 없음): title='{}', isbn13={}",
                    (item != null ? item.getTitle() : "null item"), (item != null ? item.getIsbn13() : "null"));
            return false;
        }
        return true;
    }

    private boolean isValidEBookItem(AladinBookItem item) {
        // ★수정★: isbn13이 Long 타입이므로 null 체크만 수행
        if (item == null || item.getIsbn13() == null) {
            log.warn("부적합한 eBook 아이템 (ISBN13 없음): title='{}', isbn13={}",
                    (item != null ? item.getTitle() : "null item"), (item != null ? item.getIsbn13() : "null"));
            return false;
        }
        if (!"EBOOK".equalsIgnoreCase(item.getMallType())) {
            log.warn("eBook으로 예상했으나 mallType이 다름 ({}): title='{}', isbn13={}", item.getMallType(), item.getTitle(), item.getIsbn13());
            return false;
        }
        return true;
    }

    private Mono<Book> processBookItem(AladinBookItem apiItem, SyncResult result) {
        // ★수정★: apiItem.getIsbn13()은 Long 타입. Book 엔티티의 ID도 Long 타입이어야 함.
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
                            log.warn("AladinBookItem을 Book 엔티티로 변환 실패: ISBN={}", apiItem.getIsbn13());
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
        // Book 엔티티에 mallType 필드가 있고, 업데이트하고 싶다면 아래 주석 해제 및 수정
        // if (apiItem.getMallType() != null && (existingBook.getMallType() == null || !existingBook.getMallType().equalsIgnoreCase(apiItem.getMallType()))) {
        //    existingBook.setMallType(apiItem.getMallType().toUpperCase());
        //    changed = true;
        // }
        return changed;
    }

    private Book convertToBookEntity(AladinBookItem item) {
        try {
            LocalDate publicationDate = Book.parseDate(item.getPubDate());
            // ★수정★: item.getIsbn13()은 Long 타입. Book 엔티티의 setIsbn13도 Long을 받아야 함.
            return Book.aladdinBuilder()
                    .title(item.getTitle())
                    .cover(item.getCover())
                    .writer(item.getAuthor())
                    .publicationDate(publicationDate)
                    .publishing(item.getPublisher())
                    .price(item.getPriceStandard())
                    .isbn13(item.getIsbn13()) // ★수정★: Long 타입 그대로 전달
                    // Book 엔티티에 mallType 필드가 있다면 여기서 설정
                    // .mallType(item.getMallType() != null ? item.getMallType().toUpperCase() : null)
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
                    log.info("이번 배치에서 총 {}권의 책 정보를 저장 또는 업데이트 완료 (누적 신규: {}, 누적 업데이트: {}).",
                            booksToSaveOrUpdate.size(),
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
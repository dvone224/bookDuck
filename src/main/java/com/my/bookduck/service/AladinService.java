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

    /**
     * 특정 키워드로 알라딘 상품 검색 API를 통해 eBook 정보를 대량으로 가져와 DB에 저장/업데이트합니다.
     *
     * @param searchKeyword 검색할 키워드
     * @param queryType     검색어 종류 (예: "Keyword", "Title", "Publisher")
     * @param categoryId    특정 카테고리 ID (0이면 전체, 선택 사항)
     * @param maxPagesToFetch 한 번의 호출로 가져올 최대 페이지 수 (API 제한 및 부하 고려)
     * @return Mono<SyncResult> 동기화 결과 (이번 작업에 대한 정보)
     */
    public Mono<SyncResult> fetchAndStoreEBooksBySearch(String searchKeyword, String queryType, int categoryId, int maxPagesToFetch) {
        String 작업설명 = String.format("eBook_search(keyword:%s, type:%s, catId:%d)", searchKeyword, queryType, categoryId);
        log.info("알라딘 API eBook 대량 검색 및 저장 작업 시작: {}", 작업설명);
        SyncResult result = new SyncResult(); // 작업 결과를 담을 객체
        result.setStartPage(1); // 검색은 항상 1페이지부터 시작한다고 가정

        // ★주의★: 상품 검색 API는 한 페이지에 최대 100개까지 가능합니다.
        final int maxResultsPerSearchPage = 100; // 상품 검색 API의 MaxResults 한계값

        AtomicInteger currentPage = new AtomicInteger(1);
        AtomicInteger totalFetchedItemsThisRun = new AtomicInteger(0);
        // API 응답의 totalResults를 저장할 변수 (첫 응답에서 설정)
        AtomicInteger apiTotalResults = new AtomicInteger(Integer.MAX_VALUE);

        return Flux.defer(() -> {
                    // 첫 페이지를 먼저 호출하여 totalResults를 얻고, 추가 페이지 가져올지 결정
                    if (currentPage.get() == 1) {
                        return fetchAladinSearchPage(searchKeyword, queryType, categoryId, currentPage.get(), maxResultsPerSearchPage)
                                .doOnSuccess(response -> {
                                    if (response != null && response.getTotalResults() > 0) {
                                        apiTotalResults.set(response.getTotalResults());
                                        log.info("[{}] 첫 페이지 API 응답: totalResults={}", 작업설명, response.getTotalResults());
                                    } else {
                                        apiTotalResults.set(0); // 결과 없음
                                        log.info("[{}] 첫 페이지 API 응답: 결과 없음.", 작업설명);
                                    }
                                });
                    }
                    // 이미 totalResults를 알고 있다면, 추가 페이지 요청은 생략될 수 있음 (아래 takeWhile 조건에서 처리)
                    return Mono.empty(); // 실제로는 이 부분이 실행되지 않도록 takeWhile에서 제어
                })
                .expand(lastResponse -> { // 이전 응답을 기반으로 다음 요청 생성
                    int nextPage = currentPage.incrementAndGet();
                    int fetchedSoFar = totalFetchedItemsThisRun.get(); // 이전 expand 단계까지 누적된 아이템 수
                    // (실제로는 Flux.range 와 유사하게 페이지 번호로 제어하는게 더 명확)

                    // 가져올 페이지 수 제한 (maxPagesToFetch)
                    // API가 제공하는 전체 페이지 수 제한 (apiTotalResults / maxResultsPerSearchPage)
                    // 둘 중 작은 값까지, 또는 totalResults가 0이면 중단
                    if (apiTotalResults.get() == 0 || nextPage > maxPagesToFetch || ((nextPage -1) * maxResultsPerSearchPage >= apiTotalResults.get())) {
                        log.info("[{}] 더 이상 가져올 페이지가 없거나 최대 페이지 도달. 다음 페이지: {}, API 총 결과: {}, 최대 요청 페이지: {}",
                                작업설명, nextPage, apiTotalResults.get(), maxPagesToFetch);
                        return Mono.empty(); // 확장 중단
                    }

                    log.info("[{}] 다음 페이지({}) eBook 검색 시도.", 작업설명, nextPage);
                    return fetchAladinSearchPage(searchKeyword, queryType, categoryId, nextPage, maxResultsPerSearchPage)
                            .delayElement(Duration.ofMillis(200)); // ★추가★: 연속 API 호출 시 약간의 딜레이 (Rate Limiting 방지)
                })
                // expand는 첫번째 요소도 포함하므로, 첫번째 요소 처리를 위해 concatMap 또는 flatMap 사용
                // 여기서는 Flux.range를 사용하여 페이지 번호를 생성하고, 각 페이지마다 API를 호출하는 방식으로 변경하는 것이 더 직관적일 수 있습니다.
                // 아래는 Flux.range를 사용한 대체 방식입니다.
                // --- Flux.range 사용 방식으로 변경 ---
                .thenMany(Flux.defer(() -> { // thenMany를 사용하여 위의 expand 결과를 버리고 새로운 Flux 시작
                    if (apiTotalResults.get() == 0 && currentPage.get() == 1) { // 첫 호출에서 결과가 없었으면
                        return Flux.empty();
                    }
                    // 실제 가져올 총 페이지 수 계산
                    int totalAvailablePagesFromApi = (apiTotalResults.get() + maxResultsPerSearchPage - 1) / maxResultsPerSearchPage;
                    int pagesToProcess = Math.min(totalAvailablePagesFromApi, maxPagesToFetch);
                    // currentPage가 1일때 이미 첫페이지를 가져왔다면, 2페이지부터 시작
                    // 여기서는 첫 페이지 포함하여 다시 range로 처리 (중복 호출 가능성 있으므로 주의 또는 첫페이지 호출 분리)

                    // 더 명확하게, 첫 페이지 호출은 별도로 하고, Flux.range는 2페이지부터 시작하도록 수정
                    // 첫 페이지 데이터 처리
                    Mono<AladinApiResponse> firstPageMono;
                    if(currentPage.get() == 1 && apiTotalResults.get() == Integer.MAX_VALUE) { // 아직 첫페이지 호출 전이라면
                        firstPageMono = fetchAladinSearchPage(searchKeyword, queryType, categoryId, 1, maxResultsPerSearchPage)
                                .doOnSuccess(response -> {
                                    if (response != null && response.getTotalResults() > 0) {
                                        apiTotalResults.set(response.getTotalResults());
                                        result.setLastAttemptedPage(1); // 시도한 마지막 페이지 기록
                                        currentMaxProcessedPageForSearch.set(1); // 실제 처리된 페이지
                                        log.info("[{}] 첫 페이지 API 응답: totalResults={}", 작업설명, response.getTotalResults());
                                    } else {
                                        apiTotalResults.set(0);
                                        log.info("[{}] 첫 페이지 API 응답: 결과 없음.", 작업설명);
                                    }
                                });
                    } else {
                        firstPageMono = Mono.empty(); // 이미 처리되었거나 필요 없음
                    }

                    // 나머지 페이지 데이터 처리 (2페이지부터)
                    Flux<AladinApiResponse> subsequentPagesFlux = Flux.defer(() -> {
                        if (apiTotalResults.get() == 0) return Flux.empty(); // 첫 페이지에서 결과 없으면 더 진행 안함

                        int actualMaxPages = (apiTotalResults.get() + maxResultsPerSearchPage - 1) / maxResultsPerSearchPage;
                        int limitPages = Math.min(actualMaxPages, maxPagesToFetch);

                        if (limitPages < 2) return Flux.empty(); // 1페이지만 있거나, maxPagesToFetch가 1이면 추가 페이지 없음

                        // currentPage.get()은 첫 페이지 호출 후 1 또는 그 이상일 수 있음.
                        // Flux.range는 시작 페이지부터 개수를 의미하므로, (2, limitPages - 1) 또는 (start, count) 형태
                        int startFetchingFromPage = 2; // 항상 2페이지부터
                        int countOfSubsequentPages = limitPages - 1; // 2페이지부터 가져올 페이지 수

                        if (countOfSubsequentPages <= 0) return Flux.empty();

                        log.info("[{}] 2페이지부터 최대 {}페이지까지 (총 {}개) 추가 검색 시도", 작업설명, startFetchingFromPage + countOfSubsequentPages -1, countOfSubsequentPages);
                        return Flux.range(startFetchingFromPage, countOfSubsequentPages)
                                .delayElements(Duration.ofMillis(250)) // 연속 API 호출 시 딜레이
                                .concatMap(pageIdx -> {
                                    result.setLastAttemptedPage(pageIdx); // 시도한 마지막 페이지 기록
                                    currentMaxProcessedPageForSearch.set(pageIdx); // 실제 처리된 페이지
                                    return fetchAladinSearchPage(searchKeyword, queryType, categoryId, pageIdx, maxResultsPerSearchPage);
                                });
                    });
                    // 첫 페이지 Mono와 나머지 페이지 Flux를 합침
                    return Flux.concat(firstPageMono, subsequentPagesFlux);
                }))
                // --- Flux.range 방식 종료 ---
                .filter(response -> response != null && response.getItem() != null && !response.getItem().isEmpty()) // 유효한 응답만 필터링
                .doOnNext(response -> {
                    totalFetchedItemsThisRun.addAndGet(response.getItem().size());
                    result.setTotalApiItems(totalFetchedItemsThisRun.get()); // API에서 가져온 총 아이템 수 업데이트
                })
                .flatMapIterable(AladinApiResponse::getItem) // AladinBookItem 스트림으로 변환
                .filter(this::isValidEBookItem) // eBook 아이템 유효성 검사 (ISBN, mallType 등)
                .publishOn(Schedulers.boundedElastic()) // DB 작업을 위한 스레드풀로 전환
                .flatMap(apiItem -> processBookItem(apiItem, result)) // 기존 메소드 재활용 (신규/업데이트 카운트)
                .collectList()
                .flatMap(booksToSaveOrUpdate ->
                        saveBooksInBatch(booksToSaveOrUpdate, result) // 기존 메소드 재활용
                                .onErrorResume(saveError -> {
                                    log.error("[{}] eBook 대량 검색 결과 DB 저장 중 오류 발생!", 작업설명, saveError);
                                    result.setErrorOccurred(true);
                                    return Mono.just(result);
                                })
                )
                .doOnSuccess(finalResult -> {
                    // result.setActualLastProcessedPage(...); 이 값은 위에서 setLastAttemptedPage로 유사하게 설정됨
                    // 단순 검색 수집이므로 SyncStatus 업데이트는 선택적
                    log.info("[{}] 작업 완료. 총 API 아이템: {}, 신규 저장: {}, 업데이트: {}",
                            작업설명, finalResult.getTotalApiItems(), finalResult.getSavedCount(), finalResult.getUpdatedCount());
                })
                .onErrorResume(finalError -> {
                    log.error("[{}] eBook 대량 검색 작업 중 최종 에러 발생: {}", 작업설명, finalError.getMessage(), finalError);
                    result.setErrorOccurred(true);
                    return Mono.just(result);
                });
    }
    // currentMaxProcessedPageForSearch 필드 추가 (fetchAndStoreEBooksBySearch 메소드용)
    private final AtomicInteger currentMaxProcessedPageForSearch = new AtomicInteger(0);


    /**
     * 알라딘 상품 검색 API를 호출하는 내부 메소드 (ItemSearch.aspx 사용)
     *
     * @param keyword 검색어
     * @param queryType 검색 타입
     * @param categoryId 카테고리 ID
     * @param pageNumber 페이지 번호
     * @param maxResults 페이지당 결과 수
     * @return Mono<AladinApiResponse>
     */
    private Mono<AladinApiResponse> fetchAladinSearchPage(String keyword, String queryType, int categoryId, int pageNumber, int maxResults) {
        String 작업명 = String.format("상품검색(키워드:%s,타입:%s,카테고리:%d,페이지:%d)", keyword, queryType, categoryId, pageNumber);
        log.debug("알라딘 상품 검색 API 요청: {}", 작업명);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(apiUrl) // ★수정★: 상품 검색은 apiUrl (ItemSearch.aspx) 사용
                .queryParam("ttbkey", apiKey)
                .queryParam("Query", keyword)
                .queryParam("QueryType", queryType)
                .queryParam("MaxResults", maxResults)
                .queryParam("start", pageNumber)
                .queryParam("SearchTarget", "eBook") // eBook 검색 고정
                .queryParam("output", "js")
                .queryParam("Version", "20131101");

        if (categoryId != 0) { // 0이 아닐 경우에만 카테고리 ID 파라미터 추가
            uriBuilder.queryParam("CategoryId", categoryId);
        }

        URI uri = uriBuilder.encode(StandardCharsets.UTF_8).build().toUri();

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    log.trace("[{}] API 응답 문자열: {}", 작업명, responseBody);
                    try {
                        if (responseBody != null && responseBody.trim().startsWith("{")) {
                            AladinApiResponse responseDto = objectMapper.readValue(responseBody, com.my.bookduck.controller.response.AladinApiResponse.class);
                            if (responseDto.getItem() == null) {
                                responseDto.setItem(Collections.emptyList());
                            }
                            // totalResults가 0으로 오는 경우가 있으므로, 실제 item 개수도 확인
                            if (responseDto.getTotalResults() == 0 && responseDto.getItem() != null && !responseDto.getItem().isEmpty()) {
                                log.warn("[{}] API 응답에서 totalResults는 0이지만 item 리스트는 비어있지 않음. 실제 아이템 수: {}", 작업명, responseDto.getItem().size());
                                // 필요시 totalResults를 item.size()로 보정할 수 있으나, API 스펙을 따르는 것이 일반적
                            }
                            return Mono.just(responseDto);
                        } else {
                            log.error("[{}] API 응답이 JSON 형식이 아님. 응답: {}", 작업명, responseBody.substring(0, Math.min(responseBody.length(), 500)));
                            // 빈 응답 또는 에러 DTO 반환하여 파이프라인 계속 진행
                            AladinApiResponse emptyResponse = new AladinApiResponse();
                            emptyResponse.setItem(Collections.emptyList());
                            emptyResponse.setTotalResults(0); // 확실히 0으로 설정
                            return Mono.just(emptyResponse); // 에러 대신 빈 결과로 처리
                        }
                    } catch (Exception e) {
                        log.error("[{}] API 응답 JSON 파싱 실패: {}, 응답 일부: {}",
                                작업명, e.getMessage(), responseBody.substring(0, Math.min(responseBody.length(), 500)), e);
                        AladinApiResponse errorResponse = new AladinApiResponse();
                        errorResponse.setItem(Collections.emptyList());
                        errorResponse.setTotalResults(0);
                        return Mono.just(errorResponse); // 파싱 에러도 빈 결과로 처리
                    }
                })
                .timeout(API_TIMEOUT)
                .doOnError(error -> {
                    log.error("알라딘 상품 검색 API ({}) 호출 또는 네트워크 오류: {}", 작업명, error.getMessage(), error);
                })
                .onErrorResume(e -> { // 최종 에러 처리: 빈 응답 반환
                    log.warn("fetchAladinSearchPage 최종 에러 처리 ({}). 빈 응답 반환. 에러: {}", 작업명, e.getMessage());
                    AladinApiResponse emptyResponse = new AladinApiResponse();
                    emptyResponse.setItem(Collections.emptyList());
                    emptyResponse.setTotalResults(0);
                    return Mono.just(emptyResponse);
                });
    }
}
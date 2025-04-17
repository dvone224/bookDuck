package com.my.bookduck.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로깅 추가
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async; // Async 사용
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture; // CompletableFuture 사용
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final OllamaChatModel ollamaChatModel;

    @Value("${summarization.chunk.size}")
    private int chunkSize;

    @Value("${summarization.max-reduce-length:6000}") // 기본값 6000자
    private int maxReduceLength;

    private static final int MAX_RECURSION_DEPTH = 3; // 최대 재귀 깊이

    // --- 기존 chat 메소드 (변경 없음) ---
    public String chat(String userPrompt) {
        UserMessage userMessage = new UserMessage(userPrompt);
        Prompt prompt = new Prompt(userMessage);

        try {
            ChatResponse response = ollamaChatModel.call(prompt);
            return extractAssistantMessage(response);
        } catch (Exception e) {
            log.error("Ollama Chat API 호출 중 오류 발생: {}", e.getMessage(), e);
            return "오류: AI 모델 호출에 실패했습니다.";
        }
    }

    /**
     * 긴 텍스트 요약의 진입점.
     * @param longNovelText 전체 소설 텍스트
     * @return 최종 요약 텍스트 (CompletableFuture<String>)
     */
    @Async("summaryTaskExecutor")
    public CompletableFuture<String> summarizeNovelMapReduce(String longNovelText) {
        log.info("소설 요약 시작 (MapReduce 방식, 비동기)");
        long startTime = System.currentTimeMillis();

        // 재귀적 요약 헬퍼 메소드 호출 (초기 깊이 0)
        return summarizeRecursively(longNovelText, 0)
                .whenComplete((result, throwable) -> { // 최종 완료 시 로깅
                    long endTime = System.currentTimeMillis();
                    if (throwable != null) {
                        log.error("소설 요약 처리 중 최종 오류 발생. 총 소요 시간: {} ms", (endTime - startTime), throwable);
                    } else {
                        log.info("소설 요약 최종 완료. 총 소요 시간: {} ms", (endTime - startTime));
                    }
                });
    }

    /**
     * 재귀적으로 텍스트를 요약하는 헬퍼 메소드.
     * @param textToSummarize 요약할 텍스트 (원본 또는 중간 요약본)
     * @param depth 현재 재귀 깊이
     * @return 요약 결과 (CompletableFuture<String>)
     */
    private CompletableFuture<String> summarizeRecursively(String textToSummarize, int depth) {
        log.info("요약 시작 (Depth: {}), 입력 길이: {}", depth, textToSummarize.length());

        // 최대 재귀 깊이 초과 방지
        if (depth >= MAX_RECURSION_DEPTH) {
            log.error("최대 재귀 깊이 ({}) 도달. 요약을 중단합니다.", MAX_RECURSION_DEPTH);
            return CompletableFuture.completedFuture("오류: 요약 과정이 너무 깊어져 중단되었습니다.");
        }

        // 1. 입력 텍스트가 충분히 짧으면 바로 최종 요약 시도 (Base Case)
        if (textToSummarize.length() <= maxReduceLength) {
            log.info("입력 길이가 충분히 짧아 최종 요약을 시도합니다 (Depth: {}).", depth);
            // depth 0이면 원본 텍스트 요약, 아니면 중간 요약본 요약 -> 프롬프트 약간 다르게
            String finalPromptText = (depth == 0)
                    ? "다음 텍스트를 한국어로 간결하게 요약해 주세요. 핵심 줄거리와 주요 등장인물을 포함해야 합니다. 영어는 절대 쓰지 마세요.:\n\n" + textToSummarize
                    : "다음은 소설의 각 부분에 대한 요약입니다. 이 요약들을 종합하여 소설 전체의 내용을 잘 나타내는 최종 요약문을 한국어로 작성해 주세요. 핵심 줄거리와 주요 등장인물을 포함하여 간결하게 요약해야 합니다. 영어는 절대 쓰지 마세요.:\n\n" + textToSummarize;
            Prompt finalPrompt = new Prompt(new UserMessage(finalPromptText));

            try {
                // CompletableFuture.supplyAsync를 사용하여 LLM 호출도 비동기 스레드에서 실행되도록 함
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        log.debug("최종(Base Case) 요약 요청 시작 (Depth: {})", depth);
                        ChatResponse finalResponse = ollamaChatModel.call(finalPrompt);
                        String finalSummary = extractAssistantMessage(finalResponse);
                        log.debug("최종(Base Case) 요약 응답 수신 (Depth: {})", depth);
                        return finalSummary;
                    } catch (Exception e) {
                        log.error("최종(Base Case) 요약 단계(Depth: {})에서 Ollama API 호출 중 오류 발생: {}", depth, e.getMessage(), e);
                        throw new RuntimeException("최종 요약 생성 실패 (Depth: " + depth + ")", e);
                    }
                });
            } catch (Exception e) {
                log.error("최종(Base Case) 요약(Depth: {}) 실행 요청 중 오류: {}", depth, e.getMessage(), e);
                return CompletableFuture.failedFuture(new RuntimeException("최종 요약 실행 요청 실패 (Depth: " + depth + ")", e));
            }
        }

        // 2. 입력 텍스트가 너무 길면 MapReduce 수행 (Recursive Step)
        log.info("입력 길이가 길어 MapReduce를 수행합니다 (Depth: {}).", depth);

        // 2a. Split: 텍스트를 청크로 분할
        List<String> chunks = splitTextIntoChunks(textToSummarize, chunkSize);
        log.info("텍스트 분할 완료 (Depth: {}): {}개의 청크 생성됨", depth, chunks.size());

        // 2b. Map: 각 청크를 병렬로 요약 (인덱스 포함하여 summarizeSingleChunk 호출)
        List<CompletableFuture<String>> mapFutures = IntStream.range(0, chunks.size()) // 인덱스 생성
                .mapToObj(i -> { // 각 인덱스 i 사용
                    String chunk = chunks.get(i);
                    // supplyAsync 내부에서 chunk, 인덱스 i, 현재 depth 전달
                    return CompletableFuture.supplyAsync(() -> summarizeSingleChunk(chunk, i, depth));
                })
                .toList();

        CompletableFuture<Void> allMapFutures = CompletableFuture.allOf(mapFutures.toArray(new CompletableFuture[0]));

        // 2c. Reduce (Intermediate): 모든 부분 요약이 완료되면 결과를 합침
        return allMapFutures.thenCompose(v -> { // 모든 Map 작업 완료 후 실행
            List<String> chunkSummaries = mapFutures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            // 개별 future의 실패는 join() 시 CompletionException을 발생시킴
                            // 로깅은 summarizeSingleChunk 내부 및 여기서 추가로 가능
                            log.error("부분 요약 작업 결과 취합 중 오류 (Depth: {}): {}", depth, e.getMessage());
                            return null;
                        }
                    })
                    .filter(summary -> summary != null && !summary.startsWith("오류:"))
                    .collect(Collectors.toList());

            log.info("부분 요약 완료 (Depth: {}): {} / {} 성공", depth, chunkSummaries.size(), chunks.size());

            if (chunkSummaries.isEmpty()) {
                log.warn("성공적인 부분 요약이 없습니다 (Depth: {}).", depth);
                return CompletableFuture.completedFuture("오류: 부분 요약 생성에 모두 실패했습니다 (Depth: " + depth + ").");
            }

            String combinedSummaries = String.join("\n\n---\n\n", chunkSummaries);
            log.info("Reduce 완료 (Depth: {}), 결합된 요약 길이: {}", depth, combinedSummaries.length());

            // 2d. 재귀 호출: 합쳐진 요약을 다시 이 메소드로 보내 다음 단계 처리
            return summarizeRecursively(combinedSummaries, depth + 1); // 깊이 증가시켜 재귀 호출
        });
    }


    /**
     * 단일 텍스트 청크를 요약하는 내부 메소드. (인덱스와 depth 인자 추가)
     * @param chunk 요약할 텍스트 청크
     * @param chunkIndex 해당 청크의 인덱스 (현재 depth 내에서 0부터 시작)
     * @param depth 현재 처리 중인 재귀 깊이
     * @return 요약 결과 문자열 또는 오류 메시지
     */
    private String summarizeSingleChunk(String chunk, int chunkIndex, int depth) {
        String promptText;
        // depth 0일 때만 "N번째 부분" 프롬프트 사용
        if (depth == 0) {
            promptText = String.format("다음 텍스트는 소설의 %d번째 부분입니다. 이 부분의 핵심 내용을 영어는 절대 쓰지말고 한국어로 요약해 주세요. " +
                            "나중에 이 요약본들을 합쳐 소설 전체 줄거리를 재구성할 예정이므로, 다음 사항에 집중하여 요약해 주세요:" +
                            "1.  **주요 사건:** 이 부분에서 발생한 가장 중요한 사건은 무엇인가?\n" +
                            "2.  **인물 변화/행동:** 주요 등장인물의 감정, 관계, 목표 등에 변화가 있었는가? 중요한 결정이나 행동은 무엇인가?\n" +
                            "3.  **새로운 정보/단서:** 이야기 전개에 영향을 미칠 만한 새로운 정보, 설정, 복선 등이 등장했는가?\n" +
                            "4.  **다른 부분과의 연결 가능성:** (선택 사항, 가능하다면) 이전 부분과 연결되거나 다음 부분을 암시하는 내용이 있다면 간략히 언급해 주세요." +
                            "**요약 형식:**\n" +
                            "*   200자 내외로 간결하게 요약해 주세요.\n" +
                            "*   서술형 문장으로 작성해 주세요." +
                            "**요약할 텍스트:**\n" +
                            "[여기에 소설의 N번째 부분 텍스트를 붙여넣으세요]\n" +
                            "\n" +
                            "**출력:**\n" +
                            "[%d]번째 부분 요약:\n\n%s",
                    chunkIndex + 1, chunkIndex + 1, chunk);
        } else {
            // depth 1 이상이면 중간 요약본을 다시 요약하는 프롬프트 사용
            promptText = String.format("다음은 요약문의 일부입니다. 이 부분의 핵심 내용을 한국어로 더욱 간결하게 요약해 주세요. 영어는 절대 쓰지 마세요.:\n\n%s",
                    chunk); // 중간 요약 단계에서는 N번째라는 정보가 덜 중요할 수 있음
        }

        Prompt prompt = new Prompt(new UserMessage(promptText));
        // 로그에 depth와 chunkIndex 모두 포함
        log.debug("청크 요약 요청 시작 (Depth: {}, Index: {}, 길이: {})", depth, chunkIndex, chunk.length());
        try {
            ChatResponse response = ollamaChatModel.call(prompt);
            String summary = extractAssistantMessage(response);
            log.debug("청크 요약 응답 수신 (Depth: {}, Index: {}, 요약 길이: {})", depth, chunkIndex, summary.length());
            log.debug("청크 요약 내용 (Depth: {}, Index: {}):\n---\n{}\n---", depth, chunkIndex, summary);
            return summary;
        } catch (Exception e) {
            log.error("개별 청크 요약 중 오류 발생 (Depth: {}, Index: {}): {}", depth, chunkIndex, e.getMessage(), e); // 예외 로깅 강화
            // 예외를 다시 던져서 CompletableFuture가 실패하도록 함
            // throw new RuntimeException("부분 요약 실패 (Depth: " + depth + ", Index: " + chunkIndex + ")", e);
            // 또는 오류 메시지 반환 (현재 방식 유지)
            return "오류: 부분 요약 실패";
        }
    }

    // --- splitTextIntoChunks, extractAssistantMessage 메소드 (변경 없음) ---
    private List<String> splitTextIntoChunks(String text, int targetChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int currentIndex = 0;
        int textLength = text.length();

        // 문장 구분 기호 또는 빈 줄(단락 구분)을 찾는 정규식
        // 마침표, 물음표, 느낌표 뒤에 공백이나 줄바꿈이 오는 경우, 또는 연속된 줄바꿈(단락)
        // 마침표는 약어(Mr. 등)와 구분하기 위해 뒤에 대문자가 오지 않는 경우 등을 고려할 수 있으나,
        // 여기서는 단순화를 위해 기본적인 구분자만 사용합니다.
        Pattern sentenceEndPattern = Pattern.compile("[.?!]\\s+|[.?!]$|\\n\\s*\\n"); // 문장 끝 또는 빈 줄 패턴

        while (currentIndex < textLength) {
            int estimatedEnd = Math.min(currentIndex + targetChunkSize, textLength);

            // 목표 지점이 텍스트 끝이면 남은 부분을 마지막 청크로 추가
            if (estimatedEnd == textLength) {
                chunks.add(text.substring(currentIndex));
                break;
            }

            // 목표 지점 근처에서 가장 가까운 문장/단락 끝을 찾음 (뒤에서부터 검색)
            int bestSplitPoint = -1;
            Matcher matcher = sentenceEndPattern.matcher(text);

            // estimatedEnd 근처 또는 그 이전에서 마지막 구분자를 찾음
            int searchStart = Math.max(currentIndex, estimatedEnd - targetChunkSize / 2); // 너무 앞에서 자르지 않도록 시작점 조정 가능
            int tempSplitPoint = -1;
            while(matcher.find(searchStart)) {
                if (matcher.start() <= estimatedEnd) { // 예상 종료 지점 또는 그 이전에 발견된 구분자
                    tempSplitPoint = matcher.start() + matcher.group().length(); // 구분자 포함한 위치 다음 인덱스
                    if(tempSplitPoint > currentIndex) { // 현재 시작점보다 뒤에 있어야 함
                        bestSplitPoint = tempSplitPoint;
                        // 가능한 한 estimatedEnd에 가까운 지점을 찾기 위해 계속 탐색할 수 있지만,
                        // 여기서는 estimatedEnd 이전의 마지막 지점을 선택하도록 함.
                        // 더 복잡하게는 estimatedEnd 와 가장 가까운 지점을 고를 수도 있음.
                        // 일단은 estimatedEnd 이전 마지막 지점을 사용.
                        if(tempSplitPoint > estimatedEnd) break; // estimatedEnd를 넘어서면 더 찾을 필요 없음
                    } else {
                        // 찾은 구분자가 현재 시작점보다 앞에 있으면 무시하고 계속 탐색
                        searchStart = matcher.end(); // 다음 검색 시작 위치 조정
                    }
                } else {
                    // estimatedEnd 보다 뒤에서 찾았으면 종료
                    break;
                }
                // 혹시 모를 무한 루프 방지 위해 다음 검색 시작 위치 조정
                if (matcher.end() > searchStart) {
                    searchStart = matcher.end();
                } else {
                    searchStart++; // 한 칸씩 이동하며 재시도 (비효율적일 수 있으나 안전장치)
                }
                if(searchStart >= textLength) break;
            }


            // 적절한 분할 지점을 찾지 못했거나 너무 짧게 잘리는 경우, 그냥 targetChunkSize에서 자름 (Fallback)
            if (bestSplitPoint == -1 || bestSplitPoint <= currentIndex || bestSplitPoint < currentIndex + targetChunkSize / 3) { // 너무 짧은 청크 방지
                log.warn("적절한 문장/단락 구분 지점을 찾지 못하거나 너무 짧아, {} 위치에서 강제 분할합니다. (현재 인덱스: {})", estimatedEnd, currentIndex);
                bestSplitPoint = estimatedEnd;
                // Fallback 시, 다음 청크 시작이 단어 중간일 수 있으므로 공백을 찾아 조정 시도 (선택적 개선)
                // while (bestSplitPoint < textLength && !Character.isWhitespace(text.charAt(bestSplitPoint))) {
                //     bestSplitPoint++;
                // }
            }

            String chunk = text.substring(currentIndex, bestSplitPoint).trim(); // 앞뒤 공백 제거
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            currentIndex = bestSplitPoint;

            // 다음 청크 시작 시 불필요한 공백 제거
            while (currentIndex < textLength && Character.isWhitespace(text.charAt(currentIndex))) {
                currentIndex++;
            }
        }

        log.info("텍스트 분할 완료 (문장 경계 우선): {}개의 청크 생성됨", chunks.size());
        return chunks;
    }

    private String extractAssistantMessage(ChatResponse response) {
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            return response.getResult().getOutput().getContent();
        }
        log.warn("AI 응답에서 Assistant Message를 추출할 수 없습니다.");
        return "오류: 답변 형식 오류";
    }
}
package com.my.bookduck.controller;

import com.my.bookduck.repository.TestGroupRepository;
import com.my.bookduck.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;


    // 기존 동기 chat 엔드포인트
    @GetMapping("/chat") // 경로 명확히 분리
    @ResponseBody
    public String chat(@RequestParam String m) {
        return aiService.chat(m);
    }

    // 새로운 비동기 소설 요약 엔드포인트
    @PostMapping(value = "/summarize/novel", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public CompletableFuture<String> summarizeNovel(@RequestBody String novelText) {
        // AiService의 비동기 메소드를 호출하고 CompletableFuture를 직접 반환
        // Spring MVC가 알아서 비동기 완료 시 응답을 처리해 줍니다.
        return aiService.summarizeNovelMapReduce(novelText);
    }

    @GetMapping("")
    @ResponseBody
    public String addGroup(){
        aiService.addGroup();
        return "addGroup";
    }
}

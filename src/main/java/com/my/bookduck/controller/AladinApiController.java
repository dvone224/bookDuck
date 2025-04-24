package com.my.bookduck.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/aladin")
@RequiredArgsConstructor
public class AladinApiController {

    @Value("${aladin.api.key}")
    private String ttbKey;

    private final RestTemplate restTemplate;

    @GetMapping("/search")
    public ResponseEntity<String> searchBooks(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        StringBuilder url = new StringBuilder("http://www.aladin.co.kr/ttb/api/ItemSearch.aspx?");
        url.append("ttbkey=").append(ttbKey);
        url.append("&Query=").append(query != null ? query : "");
        url.append("&QueryType=Keyword");
        url.append("&MaxResults=10");
        url.append("&start=1");
        url.append("&SearchTarget=Book");
        url.append("&output=js");
        url.append("&Version=20131101");
        if (categoryId != null) {
            url.append("&CategoryId=").append(categoryId);
        }

        String response = restTemplate.getForObject(url.toString(), String.class);
        return ResponseEntity.ok(response);
    }
}
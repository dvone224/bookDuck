package com.my.bookduck.controller;

import com.my.bookduck.controller.request.AddBookRequest;
import com.my.bookduck.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping({"/",""})
    public String addBook(final @ModelAttribute AddBookRequest book, Model model) {
        log.info("request book ={}" , book);

        try {


        } catch (Exception e) {

        }
        return "redirect:/";
    }


}

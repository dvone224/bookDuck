package com.my.bookduck.controller;

import com.my.bookduck.controller.request.AdminAddBookRequest;
import com.my.bookduck.domain.book.Book;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.StoreService;
import com.my.bookduck.service.UserService;
import lombok.Getter;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final BookService bookService;
    private final StoreService storeService;

    @GetMapping("/manageuser")
    public String manageUser() {
        return "admin/memberList";
    }

    @GetMapping("/addbook")
    public String addBook() {return "admin/bookAddForm";}

    @GetMapping("/managepos")
    public String managePosition() {return "admin/pos";}

    @GetMapping("/managebook")
    public String manageBook() {return "admin/bookList";}

    @PostMapping("/adminaddbook")
    public String insertBook(final @ModelAttribute AdminAddBookRequest book, Model model) {
        log.info("book : {}", book);
        try{
            bookService.createBook(book);
        }catch(Exception e){
            log.error("errMsg: {}", e.getMessage());
            model.addAttribute("errMsg", e.getMessage());
            return "/admin/addbook";
        }

        return "admin/adminMain";
    }

}

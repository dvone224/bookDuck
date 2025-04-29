package com.my.bookduck.controller;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.service.BookService;
import com.my.bookduck.service.CartService;
import com.my.bookduck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserService userService;
    private final BookService bookService;

    @PostMapping({"","/"})
    public ResponseEntity addCartAjax(Long userId, Long bookId) {
        User user = userService.getUserByLoginId(userId);
        Book book = bookService.getBookById(bookId);
        try{
            cartService.createCart(user, book);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("errMsg: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}

package com.my.bookduck.service;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.Cart;
import com.my.bookduck.domain.user.User;
import com.my.bookduck.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;

    @Transactional
    public void createCart(User user, Book book) throws IllegalStateException {
        log.info("user: {}", user);
        Cart cart = GetCart(user, book);
        cartRepository.save(cart);
    }

    private Cart GetCart(User user, Book book) throws IllegalStateException {
        log.info("getUser: {}", user);
        log.info("getBook: {}", book);
        Cart cart = new Cart(user, book);
        if(cartRepository.findByUserAndBook(user,book) != null) {
            throw new IllegalStateException("이미 카트에 담긴 책입니다.");
        }
        // 소유 북에서도 확인 필요 할 듯

        return cart;
    }

}

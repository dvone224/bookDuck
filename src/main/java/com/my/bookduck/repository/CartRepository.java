package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Book;
import com.my.bookduck.domain.user.Cart;
import com.my.bookduck.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartRepository extends JpaRepository<Cart, Long> {

    List<Cart> findByUserId(Long userId);

    Cart findByUserAndBook(User user, Book book);
}

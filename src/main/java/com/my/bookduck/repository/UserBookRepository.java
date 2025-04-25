package com.my.bookduck.repository;

import com.my.bookduck.domain.user.UserBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBookRepository extends JpaRepository<UserBook, Integer> {

}

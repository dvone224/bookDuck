package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByParentIsNullOrderByNameAsc();
    List<Category> findByParentIdOrderByNameAsc(Long parentId);
}
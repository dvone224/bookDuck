package com.my.bookduck.repository;

import com.my.bookduck.domain.book.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByParentIsNullOrderByNameAsc();
    List<Category> findByParentIdOrderByNameAsc(Long parentId);
    // 이름으로 검색 (기존) - 여전히 필요할 수 있음 (예: 루트 카테고리 검색)
    Optional<Category> findByName(String name);

    // 부모 ID와 이름으로 검색 (추가)
    Optional<Category> findByParent_IdAndName(Long parentId, String name);

    // 이름으로 검색하는데 부모가 없는 경우 (루트 카테고리 검색용, 추가)
    Optional<Category> findByNameAndParentIsNull(String name);
}
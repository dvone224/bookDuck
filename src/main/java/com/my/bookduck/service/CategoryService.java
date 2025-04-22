package com.my.bookduck.service;

import com.my.bookduck.domain.book.Category;
import com.my.bookduck.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> getMainCategories() {
        return categoryRepository.findAllByParentIsNullOrderByNameAsc();
    }

    public List<Category> getSubCategories(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return categoryRepository.findByParentIdOrderByNameAsc(parentId);
    }

    public Optional<Category> findById(Long categoryId) {
        return categoryRepository.findById(categoryId);
    }

    /**
     * 주어진 대분류 ID와 그 하위 모든 소분류 ID들을 Set<Long>으로 반환합니다.
     * @param mainCategoryId 대분류 카테고리 ID
     * @return 대분류 ID 자신과 모든 소분류 ID를 포함하는 Set (없으면 빈 Set)
     */
    public Set<Long> getAllSubCategoryIdsIncludingMain(Long mainCategoryId) {
        if (mainCategoryId == null) {
            return Set.of();
        }
        // 자기 자신 ID 추가
        Set<Long> categoryIds = new HashSet<>();
        categoryIds.add(mainCategoryId);

        // 하위 카테고리들의 ID 추가
        List<Category> subCategories = categoryRepository.findByParentIdOrderByNameAsc(mainCategoryId);
        if (subCategories != null && !subCategories.isEmpty()) {
            categoryIds.addAll(subCategories.stream().map(Category::getId).collect(Collectors.toSet()));
        }
        log.debug("Category IDs for main category {}: {}", mainCategoryId, categoryIds);
        return categoryIds;
    }
}
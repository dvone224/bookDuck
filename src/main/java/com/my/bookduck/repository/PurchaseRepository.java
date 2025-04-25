package com.my.bookduck.repository;

import com.my.bookduck.domain.store.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Integer> {
}

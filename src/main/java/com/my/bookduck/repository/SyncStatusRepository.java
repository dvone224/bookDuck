package com.my.bookduck.repository;

import com.my.bookduck.domain.book.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStatusRepository extends JpaRepository<SyncStatus, String> {
}
package com.my.bookduck.domain.book;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_status") // 테이블 이름 지정
@Getter
@Setter
@NoArgsConstructor
public class SyncStatus {
    @Id
    @Column(name = "sync_key", length = 100) // 작업 구분 키 (PK)
    private String syncKey;

    @Column(name = "last_processed_page", nullable = false)
    private int lastProcessedPage = 0; // 마지막 처리 페이지

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public SyncStatus(String syncKey, int lastProcessedPage) {
        this.syncKey = syncKey;
        this.lastProcessedPage = lastProcessedPage;
    }
}
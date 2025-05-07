package com.my.bookduck.domain.store;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Setter 추가
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.ArrayList; // import 추가
import java.util.List;

@Entity
@Getter
@Setter // Setter 추가 (또는 필요한 필드에만)
@NoArgsConstructor
public class Purchase {

    @Id
    @Column(name = "purchase_id") // DB 컬럼명 명시 (PurchaseItems와 일치하도록)
    private String purchaseId; // 필드명 변경 (PurchaseId -> purchaseId)

    private String userId;


    @CreatedDate // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 허용 안 함, 업데이트 불가
    private LocalDateTime purchasedAt; // 구매 일시 (날짜와 시간 포함)

    // mappedBy 속성 값은 PurchaseItems 클래스의 Purchase 필드명과 일치해야 함
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY) // FetchType.LAZY 유지 권장
    private List<PurchaseItems> purchaseItems = new ArrayList<>(); // 필드에서 바로 초기화

    // 모든 필드를 받는 생성자 (선택적)
    public Purchase(String purchaseId, String userId, List<PurchaseItems> purchaseItems) {
        this.purchaseId = purchaseId;
        this.userId = userId;
        this.purchaseItems = (purchaseItems != null) ? purchaseItems : new ArrayList<>();
    }

    // 연관관계 편의 메서드
    public void addPurchaseItem(PurchaseItems item) {
        this.purchaseItems.add(item);
        // PurchaseItems 엔티티에 Purchase 참조를 설정하는 로직은
        // PurchaseItems 객체를 생성/수정할 때 처리하는 것이 일반적입니다.
        // 여기서는 리스트에 추가만 담당합니다. (item.setPurchase(this)는 서비스 레이어 등에서 처리)
    }
}
package com.my.bookduck.domain.store;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(PurchaseId.class)
@ToString(exclude = {"purchase"})
public class PurchaseItems {

    @Id
    @Column(name = "isbn")
    private String isbn;

    @Id
    @Column(name= "purchase_id")
    private String purchaseId;

    @MapsId("purchaseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", referencedColumnName = "purchase_id", insertable = false, updatable = false)
    private Purchase purchase;

    // 생성자는 protected 유지
    protected PurchaseItems(String isbn, String purchaseId) {
        this.isbn = isbn;
        this.purchaseId = purchaseId;
    }

    // ✨ 정적 팩토리 메서드 추가 ✨
    public static PurchaseItems createPurchaseItem(String isbn, String purchaseId, Purchase purchase) {
        PurchaseItems item = new PurchaseItems(isbn, purchaseId);
        item.setPurchase(purchase); // 연관된 Purchase 설정
        return item;
    }

    // Purchase 설정 메서드 (정적 팩토리 내부 또는 외부에서 호출)
    // public void setPurchase(Purchase purchase) {
    //     this.purchase = purchase;
    // }
}
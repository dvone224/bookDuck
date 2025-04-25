package com.my.bookduck.service;

import com.my.bookduck.domain.store.Purchase;
import com.my.bookduck.domain.store.PurchaseItems;
import com.my.bookduck.domain.user.UserBook;
import com.my.bookduck.repository.PurchaseRepository;
import com.my.bookduck.repository.UserBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PurchaseRepository purchaseRepository;
    private final UserBookRepository userBookRepository;

    @Transactional
    public Purchase savePurchase(String orderId, String userId, String isbn, Long amount) {
        try {
            log.info("Attempting to save purchase. OrderID: {}, UserID: {}, ISBN: {}, Amount: {}", orderId, userId, isbn, amount);

            // 1. Purchase 객체 생성 및 초기화
            Purchase purchase = new Purchase(orderId, userId, new ArrayList<>());

            // 2. PurchaseItems 객체 생성 (정적 팩토리 메서드 사용)
            // ✨ 생성자 대신 정적 팩토리 메서드 호출 ✨
            PurchaseItems item = PurchaseItems.createPurchaseItem(isbn, orderId, purchase);
            // item.setPurchase(purchase); // 팩토리 메서드 내부에서 처리하거나 여기서 해도 됨

            // 3. Purchase 객체에 PurchaseItems 추가
            purchase.addPurchaseItem(item);

            // 4. Purchase 저장
            Purchase savedPurchase = purchaseRepository.save(purchase);

            // 유저북에 저장하는 로직
           // UserBook userBook = new UserBook(user, isbn);
            log.info("Purchase saved successfully. OrderID: {}", savedPurchase.getPurchaseId());
            return savedPurchase;

        } catch (Exception e) {
            log.error("Failed to save purchase. OrderID: {}, UserID: {}, ISBN: {}. Error: {}", orderId, userId, isbn, e.getMessage(), e);
            throw new RuntimeException("Purchase saving failed", e);
        }
    }
}
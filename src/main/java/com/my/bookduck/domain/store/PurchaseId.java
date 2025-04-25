package com.my.bookduck.domain.store;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class PurchaseId implements Serializable {

    private String purchaseId; // PurchaseItems의 필드명과 일치해야 함
    private String isbn;       // PurchaseItems의 필드명과 일치해야 함

}
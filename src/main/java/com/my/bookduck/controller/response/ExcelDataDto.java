package com.my.bookduck.controller.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class ExcelDataDto {
    private Long cid;           // 엑셀의 'CID' 열 (A열)
    private String categoryName; // 엑셀의 '카테고리명' 열 (B열)
    private String mall;         // 엑셀의 '몰' 열 (C열)
    private String depth1;       // 엑셀의 '1Depth' 열 (D열)
    private String depth2;       // 엑셀의 '2Depth' 열 (E열)
    private String depth3;       // 엑셀의 '3Depth' 열 (F열)
    private String depth4;       // 엑셀의 '4Depth' 열 (G열)
    private String depth5;       // 엑셀의 '5Depth' 열 (H열)
}

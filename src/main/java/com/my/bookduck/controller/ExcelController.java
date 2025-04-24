package com.my.bookduck.controller;

import com.my.bookduck.controller.response.ExcelDataDto;
import com.my.bookduck.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;

    @GetMapping("/excel")
    public void getExcel() {
        try {
            excelService.importCategoriesFromLocalFile("C:\\KMK\\aladin_Category_CID_20200626.xls");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}

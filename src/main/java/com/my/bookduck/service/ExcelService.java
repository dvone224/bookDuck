package com.my.bookduck.service; // 실제 패키지 경로

import com.my.bookduck.domain.book.Category;      // 실제 Category 엔티티 경로로 변경하세요
import com.my.bookduck.repository.CategoryRepository; // 실제 CategoryRepository 경로로 변경하세요
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExcelService {

    private final CategoryRepository categoryRepository;

    private static final int COL_CID = 0;       // CID 컬럼
    private static final int COL_MALL = 2;       // CID 컬럼
    private static final int COL_1DEPTH = 3;    // 1Depth 컬럼 (1부터 시작)
    private static final int COL_2DEPTH = 4;
    private static final int COL_3DEPTH = 5;
    private static final int COL_4DEPTH = 6;
    private static final int COL_5DEPTH = 7;
    private static final int MAX_DEPTH_COL = COL_5DEPTH; // 처리할 마지막 Depth 컬럼 인덱스
    private static final String ROOT_PARENT_NAME_KEY = "ROOT"; // 이름 기반 복합키용 루트


    @Transactional
    public void importCategory(InputStream inputStream) throws IOException {
        Map<String, Category> categoryCacheByCompositeNameKey = new HashMap<>();
        Workbook workbook = WorkbookFactory.create(inputStream);
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null) throw new IOException("Excel file does not contain any sheets.");
        log.info("sheet.getLastRowNum() = " + sheet.getLastRowNum());
        DataFormatter dataFormatter = new DataFormatter();
        for (int i = 3; i < sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            log.info("Row = " + row);
            int lastCellNum = row.getLastCellNum() - 1;
            log.info("lastCellNum = " + lastCellNum);

            for(int k = COL_MALL; k <= lastCellNum; k++) {
                if(row.getCell(k) == null) continue;
                String compositeKey = generateCompositeKeyByCellNum(row, k, dataFormatter);
                Category parentCategory = categoryCacheByCompositeNameKey.get(compositeKey);
                if(parentCategory != null) continue;
                Cell currentCellObj = row.getCell(k, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                // 2. DataFormatter를 사용하여 셀 값을 문자열로 변환합니다.
                //    (dataFormatter 변수는 이 코드 라인보다 앞에서 정의되어 있어야 합니다.)
                String Cell = dataFormatter.formatCellValue(currentCellObj).trim(); // trim()으로 양끝 공백 제거
                log.info("compositeKey: " + compositeKey);
                long cidCell = (long) row.getCell(COL_CID, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getNumericCellValue();
                if(k > COL_MALL) {
                    String generateParentCompositeKey = generateParentCompositeKeyByCellNum(row, k, dataFormatter);
                    parentCategory = categoryCacheByCompositeNameKey.get(generateParentCompositeKey);
                }
                Category currentCategory = new Category(cidCell, Cell, parentCategory);
                categoryCacheByCompositeNameKey.put(compositeKey, currentCategory);
                categoryRepository.save(currentCategory);
            }
        }
    }

    /**
     * 부모 카테고리의 복합 키를 안전하게 생성합니다. (DataFormatter 사용)
     *
     * @param row           현재 처리 중인 Row 객체
     * @param cellNum       현재 처리 중인 셀의 컬럼 인덱스 (0-based)
     * @param dataFormatter 셀 값 포맷터 (외부에서 주입받거나 생성)
     * @return 부모 카테고리의 복합 키 문자열
     */
    private String generateParentCompositeKeyByCellNum(Row row, int cellNum, DataFormatter dataFormatter) {
        String grandParentName = ROOT_PARENT_NAME_KEY; // 기본값: 최상위

        // 할아버지 셀 (cellNum - 2) 처리
        // 할아버지 셀 인덱스가 유효한 범위(COL_MALL 이상)인지 확인
        if (cellNum - 2 >= COL_MALL) {
            Cell grandParentCell = row.getCell(cellNum - 2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            // 셀이 null이 아니면 DataFormatter로 값 추출
            if (grandParentCell != null) {
                grandParentName = dataFormatter.formatCellValue(grandParentCell).trim();
                // 추출된 값이 비어있으면 ROOT로 간주 (데이터 정합성 위한 처리)
                if (grandParentName.isEmpty()) {
                    grandParentName = ROOT_PARENT_NAME_KEY;
                    log.trace("Row {}: Grandparent cell (Col {}) is blank, using ROOT.", row.getRowNum(), cellNum - 2);
                }
            }
            // else: 할아버지 셀이 null이면 기본값 ROOT_PARENT_NAME_KEY 유지
        }
        // else: 할아버지 셀 인덱스가 유효 범위 밖이면 기본값 ROOT_PARENT_NAME_KEY 유지

        String parentName = ""; // 기본값: 빈 문자열

        // 부모 셀 (cellNum - 1) 처리
        // 부모 셀 인덱스가 유효한 범위(COL_MALL 이상)인지 확인
        if (cellNum - 1 >= COL_MALL) {
            Cell parentCell = row.getCell(cellNum - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            // 셀이 null이 아니면 DataFormatter로 값 추출
            if (parentCell != null) {
                parentName = dataFormatter.formatCellValue(parentCell).trim();
            }
            // else: 부모 셀이 null이면 기본값 "" 유지

            // 부모 이름이 비어있는 경우에 대한 경고 로그 (키 생성에는 영향 없음)
            if (parentName.isEmpty()) {
                log.warn("Row {}: Parent cell (Col {}) is empty or null when generating parent key for Col {}.", row.getRowNum(), cellNum - 1, cellNum);
            }
        } else {
            // 부모 셀 인덱스가 유효 범위 밖이면 에러 상황
            log.error("Row {}: Invalid parent cell index ({}) when generating parent key for Col {}.", row.getRowNum(), cellNum - 1, cellNum);
            // 에러를 나타내는 특별한 키 반환 또는 예외 처리 고려
            return "ERROR_INVALID_PARENT_INDEX";
        }

        // 최종 키 조합하여 반환
        return grandParentName + "||" + parentName; // 구분자 '||' 사용
    }

    /**
     * 현재 카테고리의 복합 키를 안전하게 생성합니다. (DataFormatter 사용)
     *
     * @param row           현재 처리 중인 Row 객체
     * @param cellNum       현재 처리 중인 셀의 컬럼 인덱스 (0-based)
     * @param dataFormatter 셀 값 포맷터 (외부에서 주입받거나 생성)
     * @return 현재 카테고리의 복합 키 문자열
     */
    private String generateCompositeKeyByCellNum(Row row, int cellNum, DataFormatter dataFormatter) {
        String parentName = ROOT_PARENT_NAME_KEY; // 기본값: 최상위

        // 부모 셀 (cellNum - 1) 처리
        // 부모 셀 인덱스가 유효한 범위(COL_MALL 이상)인지 확인
        if (cellNum - 1 >= COL_MALL) {
            Cell parentCell = row.getCell(cellNum - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            // 셀이 null이 아니면 DataFormatter로 값 추출
            if (parentCell != null) {
                parentName = dataFormatter.formatCellValue(parentCell).trim();
                // 추출된 값이 비어있으면 ROOT로 간주
                if (parentName.isEmpty()) {
                    parentName = ROOT_PARENT_NAME_KEY;
                    log.trace("Row {}: Parent cell (Col {}) is blank, using ROOT for composite key generation.", row.getRowNum(), cellNum - 1);
                }
            }
            // else: 부모 셀이 null이면 기본값 ROOT_PARENT_NAME_KEY 유지
        }
        // else: 부모 셀 인덱스가 유효 범위 밖이면 (cellNum이 COL_MALL일 때) 기본값 ROOT_PARENT_NAME_KEY 유지

        String currentName = ""; // 기본값: 빈 문자열

        // 현재 셀 (cellNum) 처리
        Cell currentCell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        // 셀이 null이 아니면 DataFormatter로 값 추출
        if (currentCell != null) {
            currentName = dataFormatter.formatCellValue(currentCell).trim();
        }
        // else: 현재 셀이 null이면 기본값 "" 유지

        // 현재 이름이 비어있는 경우에 대한 경고 로그 (키 생성에는 영향 없음)
        if (currentName.isEmpty()) {
            log.warn("Row {}: Current cell (Col {}) is empty or null when generating composite key.", row.getRowNum(), cellNum);
        }

        // 최종 키 조합하여 반환
        return parentName + "||" + currentName; // 구분자 '||' 사용
    }


//    private String generateParentCompositeKeyByCellNum(Row row, int CellNum) {
//        String grandParentName = CellNum - 1 == COL_MALL ? ROOT_PARENT_NAME_KEY : row.getCell(CellNum - 2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
//        String parentName = row.getCell(CellNum - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
//        return grandParentName + "||" + parentName; // 구분자 '||' 사용
//    }
//    private String generateCompositeKeyByCellNum(Row row, int CellNum) {
//        String parentName = CellNum == COL_MALL ? ROOT_PARENT_NAME_KEY : row.getCell(CellNum - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
//        String currentName = row.getCell(CellNum, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
//        return parentName + "||" + currentName; // 구분자 '||' 사용
//    }

    private String generateCompositeKeyByCategory(Category category) {
        return category.getParent().getName() + "||" + category.getName(); // 구분자 '||' 사용
    }

    // --- 로컬 파일 처리 메소드 ---
    @Transactional
    public void importCategoriesFromLocalFile(String filePath) throws IOException, IllegalStateException {
        if (filePath == null || filePath.trim().isEmpty())
            throw new IllegalArgumentException("File path cannot be null or empty.");
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) throw new FileNotFoundException("Excel file not found at path: " + filePath);
        if (!Files.isRegularFile(path))
            throw new IllegalArgumentException("Path provided is not a regular file: " + filePath);
        if (!isExcelFile(filePath)) throw new IllegalArgumentException("Invalid file format. Path: " + filePath);

        log.info("Attempting to import categories from local file: {}", filePath);
        try (InputStream inputStream = Files.newInputStream(path)) {
            importCategory(inputStream);
        } catch (IOException | IllegalStateException e) {
            log.error("Error occurred while importing from local file: {}", filePath, e);
            throw e;
        }
    }

    // --- MultipartFile 처리 메소드 ---
    @Transactional
    public void importCategoriesFromExcel(MultipartFile file) throws IOException, IllegalStateException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Uploaded Excel file is empty or null.");
        String originalFilename = file.getOriginalFilename();
        if (!isExcelFile(originalFilename))
            throw new IllegalArgumentException("Invalid file format. Filename: " + originalFilename);

        log.info("Attempting to import categories from uploaded file: {}", originalFilename);
        try (InputStream inputStream = file.getInputStream()) {
            importCategory(inputStream);
        } catch (IOException | IllegalStateException e) {
            log.error("Error occurred while importing from uploaded file: {}", originalFilename, e);
            throw e;
        }
    }

    // --- 헬퍼 메소드들 ---

    /**
     * Cell 값을 안전하게 문자열로 읽음
     */
    private String getStringCellValue(Cell cell) {
        if (cell == null) return null;
        DataFormatter formatter = new DataFormatter();
        try {
            String cellValue = formatter.formatCellValue(cell);
            return (cellValue != null) ? cellValue.trim() : null;
        } catch (Exception e) {
            log.error("Error formatting string cell value at Row: {}, Col: {}. Error: {}", cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return null;
        }
    }

    /**
     * 파일 이름으로 Excel 파일 확장자 확인
     */
    private boolean isExcelFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String lowerCaseFileName = fileName.toLowerCase();
        return lowerCaseFileName.endsWith(".xlsx") || lowerCaseFileName.endsWith(".xls");
    }
}
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelService {

    private final CategoryRepository categoryRepository;

    // --- 상수 정의 (최신 엑셀 구조 반영) ---
    private static final int COL_CID = 0;       // CID 컬럼
    private static final int COL_1DEPTH = 1;    // 1Depth 컬럼 (1부터 시작)
    private static final int COL_2DEPTH = 2;
    private static final int COL_3DEPTH = 3;
    private static final int COL_4DEPTH = 4;
    private static final int COL_5DEPTH = 5;
    private static final int MAX_DEPTH_COL = COL_5DEPTH; // 처리할 마지막 Depth 컬럼 인덱스
    private static final String ROOT_PARENT_NAME_KEY = "ROOT"; // 이름 기반 복합키용 루트

    @Transactional
    public void importCategoriesFromExcel(InputStream inputStream) throws IOException, IllegalStateException {
        Map<String, Category> categoryCacheByCompositeNameKey = new HashMap<>();

        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new IOException("Excel file does not contain any sheets.");
            log.info("Processing categories from sheet '{}'. Total rows: {}", sheet.getSheetName(), sheet.getLastRowNum() + 1);

            int headerRowsToSkip = 1;
            for (int i = headerRowsToSkip; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cidCell = row.getCell(COL_CID, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Integer rowExcelCid = getIntegerCellValue(cidCell);

                Category currentParent = null;
                Category lastCategoryInRow = null;

                for (int colIdx = COL_1DEPTH; colIdx <= MAX_DEPTH_COL; colIdx++) {
                    Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String categoryName = getStringCellValue(cell);
                    if (categoryName == null || categoryName.trim().isEmpty()) break;
                    categoryName = categoryName.trim();

                    String parentName = (currentParent != null) ? currentParent.getName() : null;
                    String compositeNameKey = generateCompositeKeyByName(parentName, categoryName);

                    Category currentCategory = categoryCacheByCompositeNameKey.get(compositeNameKey);

                    if (currentCategory == null) {
                        // ====[ 수정: DB 조회 생략 후 즉시 저장 시도, 실패 시 조회 ]====
                        log.info("Row {}, Col {}: Category with key '{}' not in cache. Attempting to create and save.",
                                i, colIdx, compositeNameKey);
                        Category newCategory = new Category(categoryName, currentParent);
                        try {
                            // 즉시 저장 시도 (DB가 ID 생성, Cascade로 부모 처리)
                            currentCategory = categoryRepository.save(newCategory);
                            categoryCacheByCompositeNameKey.put(compositeNameKey, currentCategory); // 성공 시 캐시에 추가
                            log.debug("Row {}, Col {}: Saved new category '{}' (key: {}) with DB ID {}.", i, colIdx, categoryName, compositeNameKey, currentCategory.getId());
                        } catch (DataIntegrityViolationException e) {
                            // 저장 실패 -> 아마도 Unique 제약조건 위반 (이미 DB에 존재)
                            log.warn("Row {}, Col {}: Failed to save new category with key '{}' due to possible duplicate. Attempting to find existing.",
                                    i, colIdx, compositeNameKey, e);
                            // DB에서 다시 조회 시도
                            Optional<Category> existingCategoryOpt;
                            Long parentId = (currentParent != null) ? currentParent.getId() : null; // 부모 ID 가져오기
                            if (parentId == null) {
                                existingCategoryOpt = categoryRepository.findByNameAndParentIsNull(categoryName);
                            } else {
                                existingCategoryOpt = categoryRepository.findByParent_IdAndName(parentId, categoryName);
                            }

                            if (existingCategoryOpt.isPresent()) {
                                currentCategory = existingCategoryOpt.get();
                                categoryCacheByCompositeNameKey.put(compositeNameKey, currentCategory); // 찾았으니 캐시에 넣기
                                log.info("Row {}, Col {}: Found existing category in DB after save failure. Key: '{}', DB ID: {}", i, colIdx, compositeNameKey, currentCategory.getId());
                            } else {
                                // DB에서도 못 찾으면 심각한 문제 (Unique 제약조건 외 다른 문제)
                                log.error("Row {}, Col {}: Failed to save and subsequently find category with key '{}'. Aborting.", i, colIdx, compositeNameKey);
                                throw new IllegalStateException("Failed to save or find category: " + categoryName + " (key: " + compositeNameKey + ")", e);
                            }
                        } catch (Exception e) { // 그 외 예상치 못한 저장 에러
                            log.error("Error saving new category '{}' (key: {}): {}", categoryName, compositeNameKey, e.getMessage(), e);
                            throw new IllegalStateException("Failed to save category: " + categoryName + " (key: " + compositeNameKey + ")", e);
                        }
                        // ====[ 수정 끝 ]====
                    } else {
                        // 캐시에 있으면 건너뛰기
                        log.debug("Row {}, Col {}: Found category with key '{}' in cache. Skipping creation.", i, colIdx, compositeNameKey);
                    }

                    currentParent = currentCategory;
                    lastCategoryInRow = currentCategory;
                } // End of column loop

                // --- 현재 행의 마지막 카테고리에 Excel CID 값 할당 ---
                if (lastCategoryInRow != null && rowExcelCid != null) {
                    String lastCategoryKey = generateCompositeKeyByName(
                            (lastCategoryInRow.getParent() != null ? lastCategoryInRow.getParent().getName() : null),
                            lastCategoryInRow.getName()
                    );

                    // excelCid 필드 값 업데이트 필요 여부 확인
                    if (!Objects.equals(lastCategoryInRow.getCid(), rowExcelCid)) {
                        log.info("Row {}: Assigning/Updating Excel CID {} to category '{}' (key: {}, DB ID: {})",
                                i, rowExcelCid, lastCategoryInRow.getName(), lastCategoryKey, lastCategoryInRow.getId());
                        lastCategoryInRow.setCid(rowExcelCid);
                        try {
                            // 변경된 값 저장 (Dirty Checking에 의존해도 되지만 명시적 호출)
                            categoryRepository.save(lastCategoryInRow);
                            categoryCacheByCompositeNameKey.put(lastCategoryKey, lastCategoryInRow); // 캐시 객체 갱신
                        } catch (DataIntegrityViolationException e) {
                            log.error("Error updating excelCid for category '{}' (DB ID: {}) due to data integrity violation (possibly duplicate Excel CID?): {}",
                                    lastCategoryInRow.getName(), lastCategoryInRow.getId(), e.getMessage());
                            // excelCid에 unique 제약조건이 있다면 여기서 잡힐 수 있음
                            // 필요시 에러 throw 또는 경고 후 계속 진행
                            log.warn("Ignoring excelCid update failure for category '{}' due to integrity violation.", lastCategoryInRow.getName());
                        } catch (Exception e) {
                            log.error("Error updating excelCid for category '{}' (DB ID: {}): {}", lastCategoryInRow.getName(), lastCategoryInRow.getId(), e.getMessage(), e);
                        }
                    } else {
                        log.trace("Row {}: Category '{}' (key: {}) already has correct Excel CID {}.",
                                i, lastCategoryInRow.getName(), lastCategoryKey, rowExcelCid);
                    }
                } else if (lastCategoryInRow != null /* && rowExcelCid == null */) {
                    log.trace("Row {}: Excel CID is missing for category '{}'. excelCid field unchanged.", i, lastCategoryInRow.getName());
                } else if (lastCategoryInRow == null && rowExcelCid != null) {
                    log.warn("Row {}: No valid category found in hierarchy for CID {}. CID assignment skipped.", i, rowExcelCid);
                }

            } // End of row loop
            log.info("Category import finished successfully. Processed {} data rows. Composite Name Key Cache size: {}",
                    sheet.getLastRowNum() - headerRowsToSkip + 1, categoryCacheByCompositeNameKey.size());
        } catch (IOException e) {
            log.error("IOException occurred during Excel processing: {}", e.getMessage(), e);
            throw e;
        } catch (IllegalStateException e) {
            log.error("Data processing error during import: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error occurred during Excel import: {}", e.getMessage(), e);
            throw new IOException("Failed to process Excel file due to an unexpected error: " + e.getMessage(), e);
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException e) { log.error("Error closing workbook", e); }
            }
        }
    }

    /**
     * 부모 이름과 자식 이름을 결합하여 고유한 캐시 키를 생성합니다.
     */
    private String generateCompositeKeyByName(String parentName, String childName) {
        String parentKey = (parentName == null) ? ROOT_PARENT_NAME_KEY : parentName;
        return parentKey + "||" + childName; // 구분자 '||' 사용
    }

    // --- 로컬 파일 처리 메소드 ---
    @Transactional
    public void importCategoriesFromLocalFile(String filePath) throws IOException, IllegalStateException {
        if (filePath == null || filePath.trim().isEmpty()) throw new IllegalArgumentException("File path cannot be null or empty.");
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) throw new FileNotFoundException("Excel file not found at path: " + filePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Path provided is not a regular file: " + filePath);
        if (!isExcelFile(filePath)) throw new IllegalArgumentException("Invalid file format. Path: " + filePath);

        log.info("Attempting to import categories from local file: {}", filePath);
        try (InputStream inputStream = Files.newInputStream(path)) {
            importCategoriesFromExcel(inputStream);
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
        if (!isExcelFile(originalFilename)) throw new IllegalArgumentException("Invalid file format. Filename: " + originalFilename);

        log.info("Attempting to import categories from uploaded file: {}", originalFilename);
        try (InputStream inputStream = file.getInputStream()) {
            importCategoriesFromExcel(inputStream);
        } catch (IOException | IllegalStateException e) {
            log.error("Error occurred while importing from uploaded file: {}", originalFilename, e);
            throw e;
        }
    }

    // --- 헬퍼 메소드들 ---

    /** Cell 값을 안전하게 문자열로 읽음 */
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

    /** Cell 값을 안전하게 Integer 타입으로 읽음 (엔티티의 excelCid 필드용) */
    private Integer getIntegerCellValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue) && numericValue >= Integer.MIN_VALUE && numericValue <= Integer.MAX_VALUE) {
                    return (int) numericValue;
                } else {
                    log.warn("Numeric cell at Row: {}, Col: {} contains decimal or out-of-range value {}. Cannot convert to Integer excelCid.",
                            cell.getRowIndex(), cell.getColumnIndex(), numericValue);
                    return null;
                }
            } else if (cell.getCellType() == CellType.STRING) {
                String stringValue = cell.getStringCellValue().trim();
                if (stringValue.isEmpty()) return null;
                return Integer.parseInt(stringValue);
            } else {
                log.warn("Cell at Row: {}, Col: {} is not NUMERIC or STRING type (Type: {}). Cannot read as Integer excelCid.",
                        cell.getRowIndex(), cell.getColumnIndex(), cell.getCellType());
                return null;
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing Integer value from cell at Row: {}, Col: {}. Value: '{}'. Error: {}",
                    cell.getRowIndex(), cell.getColumnIndex(), getStringCellValue(cell), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error reading integer cell value at Row: {}, Col: {}. Error: {}",
                    cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return null;
        }
    }

    /** Cell 값을 안전하게 Long 타입으로 읽음 (참고용) */
    private Long getLongCellValue(Cell cell) {
        // ... (이전 코드 내용) ...
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue)) {
                    return (long) numericValue;
                } else {
                    log.warn("Numeric cell at Row: {}, Col: {} contains decimal value {}. Treating as invalid Long.",
                            cell.getRowIndex(), cell.getColumnIndex(), numericValue);
                    return null;
                }
            } else if (cell.getCellType() == CellType.STRING) {
                String stringValue = cell.getStringCellValue().trim();
                if (stringValue.isEmpty()) return null;
                return Long.parseLong(stringValue);
            } else {
                log.warn("Cell at Row: {}, Col: {} is not NUMERIC or STRING type (Type: {}). Cannot read as Long.",
                        cell.getRowIndex(), cell.getColumnIndex(), cell.getCellType());
                return null;
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing Long value from string cell at Row: {}, Col: {}. Value: '{}'. Error: {}",
                    cell.getRowIndex(), cell.getColumnIndex(), getStringCellValue(cell), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error reading long cell value at Row: {}, Col: {}. Error: {}",
                    cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return null;
        }
    }

    /** 파일 이름으로 Excel 파일 확장자 확인 */
    private boolean isExcelFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String lowerCaseFileName = fileName.toLowerCase();
        return lowerCaseFileName.endsWith(".xlsx") || lowerCaseFileName.endsWith(".xls");
    }
}
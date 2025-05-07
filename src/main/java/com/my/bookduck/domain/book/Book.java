package com.my.bookduck.domain.book;

import com.my.bookduck.domain.board.Board;
import com.my.bookduck.domain.group.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"categories", "bookInfos"})
public class Book {
    // 책
    @Id
    @Column(name = "book_id")
    private Long id;

    private String title;
    private String cover;
    private String writer;
    private LocalDate publicationDate;
    private String publishing;
    private int price;
    private String identifier;
    private String epubPath;


    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookCategory> categories;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookInfo> bookInfos;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Board> boards;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookComment> comments;

    @Builder
    public Book(String title,String cover,String writer,LocalDate publicationDate,String publishing,int price){
        this.title = title;
        this.cover = cover;
        this.writer = writer;
        this.publicationDate = publicationDate;
        this.publishing = publishing;
        this.price = price;
    }

    @Builder(builderMethodName = "aladdinBuilder", buildMethodName = "buildFromAladdin")
    public Book(String title,String cover,String writer,LocalDate publicationDate,String publishing,int price, Long isbn13){
        this.title = title;
        this.cover = cover;
        this.writer = writer;
        this.publicationDate = publicationDate;
        this.publishing = publishing;
        this.price = price;
        this.id = isbn13;
    }

    @Builder(builderMethodName = "adminBuilder", buildMethodName = "buildFromAdmin")
    public Book(String title, String cover, String writer, LocalDate publicationDate, String publishing, int price, String identifier, String epubPath){
        this.title = title;
        this.cover = cover;
        this.writer = writer;
        this.publicationDate = publicationDate;
        this.publishing = publishing;
        this.price = price;
        this.identifier = identifier;
        this.epubPath = epubPath;
    }

    /**
     * 알라딘 API 등에서 받은 날짜 문자열(예: "YYYY-MM-DD")을 LocalDate 객체로 변환합니다.
     * 파싱 실패 시 null을 반환합니다.
     * @param dateString 날짜 형식의 문자열
     * @return 변환된 LocalDate 객체 또는 null
     */
    public static LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            // 알라딘 API의 일반적인 날짜 형식은 'YYYY-MM-DD' 입니다.
            return LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException e) {
            System.err.println("날짜 파싱 오류 발생: 입력 문자열='" + dateString + "', 오류 메시지=" + e.getMessage());
            // 필요하다면 다른 날짜 형식 (예: "yyyyMMdd")도 시도해볼 수 있습니다.
            // try {
            //     return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyyMMdd"));
            // } catch (DateTimeParseException e2) {
            //     System.err.println("다른 형식 날짜 파싱 실패: " + dateString);
            // }
            return null; // 최종 파싱 실패 시 null 반환
        }
    }
}

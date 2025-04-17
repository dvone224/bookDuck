package com.my.bookduck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BookDuckApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookDuckApplication.class, args);
    }

}

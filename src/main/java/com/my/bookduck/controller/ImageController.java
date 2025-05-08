package com.my.bookduck.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Controller
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {

    @GetMapping("/getimg")
    public ResponseEntity<byte[]> getImg(String fileName) throws RuntimeException {
        log.info("Uploading image to " + fileName);

        String path;

        String osName = System.getProperty("os.name");
        if(osName.contains("Windows")){
            path = "C:\\bookduckImg\\";
        }else{
            path="/path/to/bookduckImg";
        }


        ResponseEntity<byte[]> result = null;

        try{
            File file = new File(path + fileName);
            HttpHeaders header = new HttpHeaders();
            header.add("Content-Type", Files.probeContentType(file.toPath()));
            result = new ResponseEntity<>(FileCopyUtils.copyToByteArray(file), header, HttpStatus.OK);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

}

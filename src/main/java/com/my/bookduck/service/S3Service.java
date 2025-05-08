package com.my.bookduck.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.my.bookduck.config.auth.BDUserDetails;
import com.my.bookduck.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class S3Service {
    private final AmazonS3 amazonS3;


    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public S3Service(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    /**
     * S3에 이미지 업로드 하기
     */
    public String uploadImage(MultipartFile image) throws IOException {
        String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename(); // 고유한 파일 이름 생성

        // 메타데이터 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(image.getContentType());
        metadata.setContentLength(image.getSize());

        // S3에 파일 업로드 요청 생성
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, image.getInputStream(), metadata);

        // S3에 파일 업로드
        amazonS3.putObject(putObjectRequest);

        return getPublicUrl(fileName);
    }

    private String getPublicUrl(String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, amazonS3.getRegionName(), fileName);
    }

    @Transactional
    public String  uploadImage(MultipartFile image, User user) throws IOException {


        // 기존의 프로필 이미지가 있었다면 삭제
        if(user.getImg() != null)
            amazonS3.deleteObject(bucket, user.getImg());

        // 파일 확장자 추출
        String extension = getImageExtension(image);
        String fileName = UUID.randomUUID() + "_" + user.getId() + "_profile" + extension;

        // 메타데이터 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(image.getContentType());
        metadata.setContentLength(image.getSize());

        // S3에 파일 업로드 요청 생성
        PutObjectRequest putObjectRequest = null;
        try {
            putObjectRequest = new PutObjectRequest(bucket, fileName, image.getInputStream(), metadata);
        } catch (IOException e) {
            log.error(String.valueOf(e.getCause()));
            throw new IOException("서버오류: 이미지 업로드에 실패하였습니다.");
        }

        // S3에 파일 업로드
        amazonS3.putObject(putObjectRequest);

        // 업로드 후 멤버 객체에 URL 할당
        String publicUrl = getPublicUrl(fileName);

        return publicUrl;
    }

    private String getImageExtension(MultipartFile image) {
        String extension = "";
        String originalFilename = image.getOriginalFilename();

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return extension;
    }


}
package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, String> {
    Optional<FileUpload> findByFileMd5(String fileMd5);
}

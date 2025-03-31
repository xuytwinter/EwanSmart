package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentVector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentVectorRepository extends JpaRepository<DocumentVector, Long> {
    List<DocumentVector> findByFileMd5(String fileMd5); // 查询某文件的所有分块
}

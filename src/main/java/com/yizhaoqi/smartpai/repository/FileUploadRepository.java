package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, String> {
    Optional<FileUpload> findByFileMd5(String fileMd5);
    
    /**
     * 查询用户可访问的所有文件
     * 包括：1. 用户自己上传的文件
     *      2. 公开的文件
     *      3. 用户所属组织的文件
     *
     * @param userId 用户ID
     * @param orgTags 用户所属的组织标签列表（逗号分隔）
     * @return 用户可访问的文件列表
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true OR (f.orgTag IN :orgTagList AND f.isPublic = false)")
    List<FileUpload> findAccessibleFiles(@Param("userId") String userId, @Param("orgTagList") List<String> orgTagList);
    
    /**
     * 查询用户自己上传的所有文件
     * 
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    List<FileUpload> findByUserId(String userId);
}

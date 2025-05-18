package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档管理服务类
 * 负责文档的删除等管理操作
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ElasticsearchService elasticsearchService;

    /**
     * 删除文档及其相关数据
     * 该方法将删除:
     * 1. FileUpload记录
     * 2. DocumentVector记录
     * 3. MinIO中的文件
     * 4. Elasticsearch中的向量数据
     *
     * @param fileMd5 文件MD5
     */
    @Transactional
    public void deleteDocument(String fileMd5) {
        logger.info("开始删除文档: {}", fileMd5);
        
        try {
            // 获取文件信息以获取文件名
            FileUpload fileUpload = fileUploadRepository.findById(fileMd5)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));
            
            // 1. 删除Elasticsearch中的数据
            try {
                elasticsearchService.deleteByFileMd5(fileMd5);
                logger.info("成功从Elasticsearch删除文档: {}", fileMd5);
            } catch (Exception e) {
                logger.error("从Elasticsearch删除文档时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }
            
            // 2. 删除MinIO中的文件
            try {
                String objectName = "merged/" + fileUpload.getFileName();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket("uploads")
                                .object(objectName)
                                .build()
                );
                logger.info("成功从MinIO删除文件: {}", objectName);
            } catch (Exception e) {
                logger.error("从MinIO删除文件时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }
            
            // 3. 删除DocumentVector记录
            try {
                documentVectorRepository.deleteByFileMd5(fileMd5);
                logger.info("成功删除文档向量记录: {}", fileMd5);
            } catch (Exception e) {
                logger.error("删除文档向量记录时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }
            
            // 4. 删除FileUpload记录
            fileUploadRepository.deleteById(fileMd5);
            logger.info("成功删除文件上传记录: {}", fileMd5);
            
            logger.info("文档删除完成: {}", fileMd5);
        } catch (Exception e) {
            logger.error("删除文档过程中发生错误: {}", fileMd5, e);
            throw new RuntimeException("删除文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 包括用户自己的文件、公开文件和用户所属组织的文件
     *
     * @param userId 用户ID
     * @param orgTags 用户所属的组织标签（逗号分隔的字符串）
     * @return 用户可访问的文件列表
     */
    public List<FileUpload> getAccessibleFiles(String userId, String orgTags) {
        logger.info("获取用户可访问文件列表: userId={}, orgTags={}", userId, orgTags);
        
        try {
            // 将逗号分隔的组织标签字符串转换为列表
            List<String> orgTagList = Arrays.stream(orgTags.split(","))
                    .filter(tag -> !tag.isEmpty())
                    .collect(Collectors.toList());
            
            // 调用Repository方法获取数据
            List<FileUpload> files = fileUploadRepository.findAccessibleFiles(userId, orgTagList);
            logger.info("成功获取用户可访问文件列表: userId={}, fileCount={}", userId, files.size());
            return files;
        } catch (Exception e) {
            logger.error("获取用户可访问文件列表失败: userId={}", userId, e);
            throw new RuntimeException("获取可访问文件列表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户上传的所有文件列表
     *
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    public List<FileUpload> getUserUploadedFiles(String userId) {
        logger.info("获取用户上传的文件列表: userId={}", userId);
        
        try {
            List<FileUpload> files = fileUploadRepository.findByUserId(userId);
            logger.info("成功获取用户上传的文件列表: userId={}, fileCount={}", userId, files.size());
            return files;
        } catch (Exception e) {
            logger.error("获取用户上传的文件列表失败: userId={}", userId, e);
            throw new RuntimeException("获取用户上传的文件列表失败: " + e.getMessage(), e);
        }
    }
} 
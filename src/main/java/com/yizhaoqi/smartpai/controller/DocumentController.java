package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档控制器类，处理文档相关操作请求
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;

    /**
     * 删除文档及其相关数据
     * 
     * @param fileMd5 文件MD5
     * @param userId 当前用户ID
     * @param role 用户角色
     * @return 删除结果
     */
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {
        
        try {
            logger.info("接收到删除文档请求: fileMd5={}, userId={}, role={}", fileMd5, userId, role);
            
            // 获取文件信息
            Optional<FileUpload> fileOpt = fileUploadRepository.findById(fileMd5);
            if (fileOpt.isEmpty()) {
                logger.warn("文档不存在: fileMd5={}", fileMd5);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "文档不存在"));
            }
            
            FileUpload file = fileOpt.get();
            
            // 权限检查：只有文件所有者或管理员可以删除
            if (!file.getUserId().equals(userId) && !"ADMIN".equals(role)) {
                logger.warn("用户无权删除文档: fileMd5={}, fileOwner={}, requestUser={}", 
                        fileMd5, file.getUserId(), userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "没有权限删除此文档"));
            }
            
            // 执行删除操作
            documentService.deleteDocument(fileMd5);
            
            logger.info("文档删除成功: fileMd5={}", fileMd5);
            return ResponseEntity.ok(Map.of("status", "success", "message", "文档删除成功"));
        } catch (Exception e) {
            logger.error("删除文档失败: fileMd5={}", fileMd5, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "删除文档失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 
     * @param userId 当前用户ID
     * @param orgTags 用户所属组织标签
     * @return 可访问的文件列表
     */
    @GetMapping("/accessible")
    public ResponseEntity<?> getAccessibleFiles(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags) {
        
        try {
            logger.info("接收到获取可访问文件请求: userId={}, orgTags={}", userId, orgTags);
            
            List<FileUpload> files = documentService.getAccessibleFiles(userId, orgTags);
            
            logger.info("成功获取可访问文件: userId={}, fileCount={}", userId, files.size());
            return ResponseEntity.ok(Map.of("status", "success", "data", files));
        } catch (Exception e) {
            logger.error("获取可访问文件失败: userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "获取可访问文件列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户上传的所有文件列表
     * 
     * @param userId 当前用户ID
     * @return 用户上传的文件列表
     */
    @GetMapping("/uploads")
    public ResponseEntity<?> getUserUploadedFiles(
            @RequestAttribute("userId") String userId) {
        
        try {
            logger.info("接收到获取用户上传文件请求: userId={}", userId);
            
            List<FileUpload> files = documentService.getUserUploadedFiles(userId);
            
            logger.info("成功获取用户上传文件: userId={}, fileCount={}", userId, files.size());
            return ResponseEntity.ok(Map.of("status", "success", "data", files));
        } catch (Exception e) {
            logger.error("获取用户上传文件失败: userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "获取用户上传文件列表失败: " + e.getMessage()));
        }
    }
} 
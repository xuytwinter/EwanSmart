package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.UploadService;
import com.yizhaoqi.smartpai.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    @Autowired
    private UploadService uploadService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaConfig kafkaConfig;

    @Autowired
    private UserService userService;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;

    public UploadController(UploadService uploadService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.uploadService = uploadService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 上传文件分片接口
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件
     * @param chunkIndex 分片索引，表示当前分片的位置
     * @param totalSize 文件总大小
     * @param fileName 文件名
     * @param totalChunks 总分片数量
     * @param orgTag 组织标签，如果未指定则使用用户的主组织标签
     * @param isPublic 是否公开，默认为false
     * @param file 分片文件对象
     * @return 返回包含已上传分片和上传进度的响应
     * @throws IOException 当文件读写发生错误时抛出
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestHeader("X-File-MD5") String fileMd5,
            @RequestHeader("X-Chunk-Index") int chunkIndex,
            @RequestHeader("X-Total-Size") long totalSize,
            @RequestHeader("X-File-Name") String fileName,
            @RequestHeader(value = "X-Total-Chunks", required = false) Integer totalChunks,
            @RequestHeader(value = "X-Org-Tag", required = false) String orgTag,
            @RequestHeader(value = "X-Is-Public", required = false, defaultValue = "false") boolean isPublic,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") String userId) throws IOException {
        
        logger.info("接收到分片上传请求 => fileMd5: {}, chunkIndex: {}, totalSize: {}, fileName: {}, fileSize: {}, totalChunks: {}, orgTag: {}, isPublic: {}, userId: {}", 
                  fileMd5, chunkIndex, totalSize, fileName, file.getSize(), totalChunks, orgTag, isPublic, userId);
        
        // 如果未指定组织标签，则获取用户的主组织标签
        if (orgTag == null || orgTag.isEmpty()) {
            try {
                logger.debug("组织标签未指定，尝试获取用户主组织标签 => userId: {}", userId);
                String primaryOrg = userService.getUserPrimaryOrg(userId);
                orgTag = primaryOrg;
                logger.info("成功获取用户主组织标签 => userId: {}, primaryOrg: {}", userId, orgTag);
            } catch (Exception e) {
                logger.error("获取用户主组织标签失败 => userId: {}, 错误: {}", userId, e.getMessage(), e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "获取用户主组织标签失败: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        }
        
        logger.info("开始处理文件分片 => fileMd5: {}, chunkIndex: {}/{}({}%), fileName: {}, orgTag: {}", 
                  fileMd5, chunkIndex, totalChunks, 
                  (totalChunks != null ? (int)((chunkIndex + 1.0) / totalChunks * 100) : "未知"),
                  fileName, orgTag);
        
        try {
            uploadService.uploadChunk(fileMd5, chunkIndex, totalSize, fileName, file, orgTag, isPublic, userId);
            logger.info("分片上传成功 => fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex);
            
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(fileMd5);
            int actualTotalChunks = uploadService.getTotalChunks(fileMd5);
            double progress = calculateProgress(uploadedChunks, actualTotalChunks);
            
            logger.info("分片上传进度 => fileMd5: {}, 已上传: {}/{} ({:.2f}%)", 
                      fileMd5, uploadedChunks.size(), actualTotalChunks, progress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("uploaded", uploadedChunks);
            response.put("progress", progress);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("分片上传失败 => fileMd5: {}, chunkIndex: {}, 错误: {}", 
                      fileMd5, chunkIndex, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "分片上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取文件上传状态接口
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件
     * @return 返回包含已上传分片和上传进度的响应
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@RequestParam("file_md5") String fileMd5) {
        logger.info("获取文件上传状态 => fileMd5: {}", fileMd5);
        try {
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(fileMd5);
            int totalChunks = uploadService.getTotalChunks(fileMd5);
            double progress = calculateProgress(uploadedChunks, totalChunks);
            
            logger.info("文件上传状态 => fileMd5: {}, 已上传分片: {}/{}, 进度: {:.2f}%", 
                      fileMd5, uploadedChunks.size(), totalChunks, progress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("uploaded", uploadedChunks);
            response.put("progress", progress);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取文件上传状态失败 => fileMd5: {}, 错误: {}", fileMd5, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取上传状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 合并文件分片接口
     *
     * @param request 包含文件MD5和文件名的请求体
     * @param userId 当前用户ID
     * @return 返回包含合并后文件访问URL的响应
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, String>> mergeFile(
            @RequestBody MergeRequest request,
            @RequestAttribute("userId") String userId) {
        logger.info("接收到合并文件请求 => fileMd5: {}, fileName: {}, userId: {}", 
                  request.fileMd5(), request.fileName(), userId);
        
        try {
            // 检查文件完整性和权限
            logger.debug("检查文件记录和权限 => fileMd5: {}, userId: {}", request.fileMd5(), userId);
            FileUpload fileUpload = fileUploadRepository.findById(request.fileMd5())
                    .orElseThrow(() -> {
                        logger.error("文件记录不存在 => fileMd5: {}", request.fileMd5());
                        return new RuntimeException("文件记录不存在");
                    });
                    
            // 确保用户有权限操作该文件
            if (!fileUpload.getUserId().equals(userId)) {
                logger.warn("权限验证失败 => 用户 {} 尝试合并不属于他的文件 {}, 实际所有者: {}", 
                          userId, request.fileMd5(), fileUpload.getUserId());
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "没有权限操作此文件");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
            
            logger.info("权限验证通过，开始合并文件 => fileMd5: {}, fileName: {}", 
                      request.fileMd5(), request.fileName());
            
            // 检查分片是否全部上传完成
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(request.fileMd5());
            int totalChunks = uploadService.getTotalChunks(request.fileMd5());
            logger.info("分片上传状态 => fileMd5: {}, 已上传: {}/{}", 
                      request.fileMd5(), uploadedChunks.size(), totalChunks);
            
            if (uploadedChunks.size() < totalChunks) {
                logger.warn("分片未全部上传，无法合并 => fileMd5: {}, 已上传: {}/{}", 
                          request.fileMd5(), uploadedChunks.size(), totalChunks);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "文件分片未全部上传，无法合并");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // 合并文件
            logger.info("开始合并文件分片 => fileMd5: {}, 分片数量: {}", request.fileMd5(), totalChunks);
            String objectUrl = uploadService.mergeChunks(request.fileMd5(), request.fileName());
            logger.info("文件分片合并成功 => fileMd5: {}, objectUrl: {}", request.fileMd5(), objectUrl);

            // 发送任务到 Kafka，包含完整的权限信息
            logger.info("创建文件处理任务 => fileMd5: {}, fileName: {}, userId: {}, orgTag: {}, isPublic: {}", 
                      request.fileMd5(), request.fileName(), fileUpload.getUserId(), 
                      fileUpload.getOrgTag(), fileUpload.isPublic());
            
            FileProcessingTask task = new FileProcessingTask(
                    request.fileMd5(),
                    objectUrl,
                    request.fileName(),
                    fileUpload.getUserId(),
                    fileUpload.getOrgTag(),
                    fileUpload.isPublic()
            );
            
            logger.info("发送文件处理任务到Kafka => topic: {}, fileMd5: {}", 
                      kafkaConfig.getFileProcessingTopic(), request.fileMd5());
            kafkaTemplate.send(kafkaConfig.getFileProcessingTopic(), task);
            logger.info("文件处理任务已发送 => fileMd5: {}", request.fileMd5());

            // 返回响应
            Map<String, String> response = new HashMap<>();
            response.put("object_url", objectUrl);
            response.put("message", "文件合并成功，任务已发送到 Kafka");
            
            logger.info("文件上传和合并流程完成 => fileMd5: {}, fileName: {}", 
                      request.fileMd5(), request.fileName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("文件合并失败 => fileMd5: {}, fileName: {}, 错误: {}", 
                      request.fileMd5(), request.fileName(), e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "文件合并失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 计算上传进度
     *
     * @param uploadedChunks 已上传的分片列表
     * @param totalChunks 总分片数量
     * @return 返回上传进度的百分比
     */
    private double calculateProgress(List<Integer> uploadedChunks, int totalChunks) {
        if (totalChunks == 0) {
            logger.warn("计算上传进度时总分片数为0");
            return 0.0;
        }
        return (double) uploadedChunks.size() / totalChunks * 100;
    }

    /**
     * 合并请求的辅助类，包含文件的MD5值和文件名
     */
    public record MergeRequest(String fileMd5, String fileName) {}
}


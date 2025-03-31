package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.service.UploadService;
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

    @Autowired
    private UploadService uploadService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaConfig kafkaConfig;

    public UploadController(UploadService uploadService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.uploadService = uploadService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 上传文件分片接口
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件
     * @param chunkIndex 分片索引，表示当前分片的位置
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
            @RequestParam("file") MultipartFile file) throws IOException {
        uploadService.uploadChunk(fileMd5, chunkIndex, totalSize, fileName, file);

        Map<String, Object> response = new HashMap<>();
        response.put("uploaded", uploadService.getUploadedChunks(fileMd5));
        response.put("progress", calculateProgress(uploadService.getUploadedChunks(fileMd5), uploadService.getTotalChunks(fileMd5)));
        return ResponseEntity.ok(response);
    }

    /**
     * 获取文件上传状态接口
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件
     * @return 返回包含已上传分片和上传进度的响应
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@RequestParam("file_md5") String fileMd5) {
        Map<String, Object> response = new HashMap<>();
        response.put("uploaded", uploadService.getUploadedChunks(fileMd5));
        response.put("progress", calculateProgress(uploadService.getUploadedChunks(fileMd5), uploadService.getTotalChunks(fileMd5)));
        return ResponseEntity.ok(response);
    }

    /**
     * 合并文件分片接口
     *
     * @param request 包含文件MD5和文件名的请求体
     * @return 返回包含合并后文件访问URL的响应
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, String>> mergeFile(@RequestBody MergeRequest request) {
        try {
            // 合并文件
           String objectUrl = uploadService.mergeChunks(request.fileMd5(), request.fileName());
            // String objectUrl = "http://127.0.0.1:9001/api/v1/download-shared-object/aHR0cDovLzEyNy4wLjAuMTo5MDAwL3VwbG9hZHMvbWVyZ2VkL2FhYS5wZGY_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD0zWlRCM0M1Q1NBVEgyVDdMNktNMSUyRjIwMjUwMzAzJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDMwM1QxMjIxMjhaJlgtQW16LUV4cGlyZXM9NDMxOTkmWC1BbXotU2VjdXJpdHktVG9rZW49ZXlKaGJHY2lPaUpJVXpVeE1pSXNJblI1Y0NJNklrcFhWQ0o5LmV5SmhZMk5sYzNOTFpYa2lPaUl6V2xSQ00wTTFRMU5CVkVneVZEZE1Oa3ROTVNJc0ltVjRjQ0k2TVRjME1UQTBOelk0TVN3aWNHRnlaVzUwSWpvaWJXbHVhVzloWkcxcGJpSjkuNkM4Q29oSXh2a3hOeFdpb0s4TFdQTDhRQlUteUpTQTREUWlac25jWEItanRqS21NU0x0bXdHT1NKUjZ6VGtuMV9EcVo2OWxMMEtoVllOS19VdWN1RlEmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0JnZlcnNpb25JZD1udWxsJlgtQW16LVNpZ25hdHVyZT01YjkwYzcyOGU5OGJiOTgwZmExZDZiZDY0M2M4N2RhNjRkNTk4NzU2YThjOWJhZTNhNzM5MTRlODFiMGM4Nzc0";

            // 发送任务到 Kafka
            FileProcessingTask task = new FileProcessingTask(
                    request.fileMd5(),
                    objectUrl, // 文件存储路径或 URL
                    request.fileName()
            );
            kafkaTemplate.send(kafkaConfig.getFileProcessingTopic(), task);

            // 返回响应
            Map<String, String> response = new HashMap<>();
            response.put("object_url", "");
            response.put("message", "文件合并成功，任务已发送到 Kafka");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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
            return 0.0;
        }
        return (double) uploadedChunks.size() / totalChunks * 100;
    }

    /**
     * 合并请求的辅助类，包含文件的MD5值和文件名
     */
    public record MergeRequest(String fileMd5, String fileName) {}
}


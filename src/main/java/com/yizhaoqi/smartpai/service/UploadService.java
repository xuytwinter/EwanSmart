package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.ChunkInfo;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.ChunkInfoRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Service
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    // 用于缓存已上传分片的信息
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 用于与 MinIO 服务器交互
    @Autowired
    private MinioClient minioClient;

    // 用于操作文件上传记录的 Repository
    @Autowired
    private FileUploadRepository fileUploadRepository;

    // 用于操作分片信息的 Repository
    @Autowired
    private ChunkInfoRepository chunkInfoRepository;

    @Autowired
    private String minioPublicUrl; // 注入 MinIO 的公共访问地址

    /**
     * 上传文件分片
     *
     * @param fileMd5 文件的 MD5 值，用于唯一标识文件
     * @param chunkIndex 分片索引，表示这是文件的第几个分片
     * @param file 要上传的分片文件
     * @throws IOException 如果文件读取失败
     */
    public void uploadChunk(String fileMd5, int chunkIndex, long totalSize, String fileName, MultipartFile file) throws IOException {
        logger.info("[uploadChunk] 开始处理分片上传请求: fileMd5={}, chunkIndex={}", fileMd5, chunkIndex);
        // 检查 file_upload 表中是否存在该 file_md5
        if (!fileUploadRepository.existsById(fileMd5)) {
            // 插入 file_upload 表
            FileUpload fileUpload = new FileUpload();
            fileUpload.setFileMd5(fileMd5);
            fileUpload.setFileName(fileName); // 文件名可以从请求中获取
            fileUpload.setTotalSize(totalSize); // 文件总大小
            fileUpload.setStatus(0); // 0 表示上传中
            fileUploadRepository.save(fileUpload);
        }

        // 检查分片是否已经上传
        if (isChunkUploaded(fileMd5, chunkIndex)) {
            logger.warn("分片已上传: fileMd5={}, chunkIndex={}", fileMd5, chunkIndex);
            return; // 忽略重复上传
        }

        // 计算分片的 MD5 值
        String chunkMd5 = DigestUtils.md5Hex(file.getBytes());
        // 构建分片的存储路径
        String storagePath = "chunks/" + fileMd5 + "/" + chunkIndex;

        try {
            // 存储到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket("uploads")
                            .object(storagePath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (io.minio.errors.ErrorResponseException e) {
            throw new RuntimeException(e);
        } catch (ErrorResponseException e) {
            throw new RuntimeException("MinIO 错误响应", e);
        } catch (InsufficientDataException e) {
            throw new RuntimeException("数据不足", e);
        } catch (InternalException e) {
            throw new RuntimeException("内部错误", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("无效密钥", e);
        } catch (InvalidResponseException e) {
            throw new RuntimeException("无效响应", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("找不到算法", e);
        } catch (ServerException e) {
            throw new RuntimeException("服务器错误", e);
        } catch (XmlParserException e) {
            throw new RuntimeException("XML 解析错误", e);
        }

        // 标记分片已上传
        markChunkUploaded(fileMd5, chunkIndex);
        // 保存分片信息到数据库
        saveChunkInfo(fileMd5, chunkIndex, chunkMd5, storagePath);
    }

    /**
     * 检查指定分片是否已上传
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     * @return 如果分片已上传返回 true，否则返回 false
     */
    public boolean isChunkUploaded(String fileMd5, int chunkIndex) {
        try {
            if (chunkIndex < 0) {
                logger.error("Invalid chunkIndex: {}", chunkIndex);
                throw new IllegalArgumentException("chunkIndex must be non-negative");
            }
            boolean isUploaded = redisTemplate.opsForValue().getBit("upload:" + fileMd5, chunkIndex);
            logger.debug("Chunk uploaded status for fileMd5: {}, chunkIndex: {} is {}", fileMd5, chunkIndex, isUploaded);
            return isUploaded;
        } catch (Exception e) {
            logger.error("Failed to check chunk upload status for fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex, e);
            return false; // 或者根据业务需求返回其他值
        }
    }

    /**
     * 标记指定分片为已上传
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     */
    public void markChunkUploaded(String fileMd5, int chunkIndex) {
        try {
            if (chunkIndex < 0) {
                logger.error("Invalid chunkIndex: {}", chunkIndex);
                throw new IllegalArgumentException("chunkIndex must be non-negative");
            }
            redisTemplate.opsForValue().setBit("upload:" + fileMd5, chunkIndex, true);
            logger.debug("Marked chunk as uploaded for fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex);
        } catch (Exception e) {
            logger.error("Failed to mark chunk as uploaded for fileMd5: {}, chunkIndex: {}", fileMd5, chunkIndex, e);
        }
    }

    /**
     * 删除文件所有分片上传标记
     *
     * @param fileMd5 文件的 MD5 值
     */
    public void deleteFileMark(String fileMd5) {
        try {
            redisTemplate.delete("upload:" + fileMd5);
            logger.debug("Deleted file mark for fileMd5: {}", fileMd5);
        } catch (Exception e) {
            logger.error("Failed to delete file mark for fileMd5: {}", fileMd5, e);
        }
    }


    /**
     * 获取已上传的分片列表
     *
     * @param fileMd5 文件的 MD5 值
     * @return 包含已上传分片索引的列表
     */
    public List<Integer> getUploadedChunks(String fileMd5) {
        logger.info("[getUploadedChunks] 获取已上传分片列表: fileMd5={}", fileMd5);
        List<Integer> uploadedChunks = new ArrayList<>();
        int totalChunks = getTotalChunks(fileMd5);
        for (int i = 0; i < totalChunks; i++) {
            if (isChunkUploaded(fileMd5, i)) {
                uploadedChunks.add(i);
            }
        }
        logger.info("已上传的分片列表: " + uploadedChunks);
        return uploadedChunks;
    }

    /**
     * 获取文件的总分片数
     *
     * @param fileMd5 文件的 MD5 值
     * @return 文件的总分片数
     */
    public int getTotalChunks(String fileMd5) {
        logger.info("[getTotalChunks] 获取文件总分片数: fileMd5={}", fileMd5);
        Optional<FileUpload> fileUpload = fileUploadRepository.findById(fileMd5);
        int totalChunks = fileUpload.map(f -> (int) Math.ceil((double) f.getTotalSize() / (5 * 1024 * 1024))).orElse(0);
        logger.info("文件的总分片数: " + totalChunks);
        return totalChunks;
    }

    /**
     * 保存分片信息到数据库
     *
     * @param fileMd5 文件的 MD5 值
     * @param chunkIndex 分片索引
     * @param chunkMd5 分片的 MD5 值
     * @param storagePath 分片的存储路径
     */
    private void saveChunkInfo(String fileMd5, int chunkIndex, String chunkMd5, String storagePath) {
        logger.info("保存分片信息到数据库，文件MD5: " + fileMd5 + ", 分片索引: " + chunkIndex);
        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5(fileMd5);
        chunkInfo.setChunkIndex(chunkIndex);
        chunkInfo.setChunkMd5(chunkMd5);
        chunkInfo.setStoragePath(storagePath);
        chunkInfoRepository.save(chunkInfo);
        logger.info("分片信息已保存");

    }

    /**
     * 合并所有分片
     *
     * @param fileMd5 文件的 MD5 值
     * @param fileName 文件名
     * @return 合成文件的访问 URL
     */
    public String mergeChunks(String fileMd5, String fileName) {
        logger.info("[mergeChunks] 开始合并文件分片: fileMd5={}", fileMd5);
        List<ChunkInfo> chunks = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5);
        List<String> partPaths = chunks.stream().map(ChunkInfo::getStoragePath).collect(Collectors.toList());

        String mergedPath = "merged/" + fileName;
        try {
            // 合并分片
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket("uploads")
                            .object(mergedPath)
                            .sources(partPaths.stream()
                                    .map(path -> ComposeSource.builder().bucket("uploads").object(path).build())
                                    .collect(Collectors.toList()))
                            .build()
            );
            logger.info("分片合并完成");

            // 清理分片文件
            logger.info("开始清理分片文件");

            // 清理分片文件
            for (String path : partPaths) {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket("uploads").object(path).build());
            }
            logger.info("分片文件已清理");

            // 删除 Redis 中的分片状态记录
            logger.info("删除 Redis 中的分片状态记录");
            deleteFileMark(fileMd5);
            logger.info("分片状态记录已删除");

            // 更新文件状态
            logger.info("更新文件状态");
            FileUpload fileUpload = fileUploadRepository.findById(fileMd5).orElseThrow();
            fileUpload.setStatus(1); // 已完成
            fileUpload.setMergedAt(LocalDateTime.now());
            fileUploadRepository.save(fileUpload);
            logger.info("文件状态已更新");

            // 生成预签名 URL（有效期为 1 小时）
            logger.info("生成预签名 URL");
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket("uploads")
                            .object(mergedPath)
                            .expiry(1, TimeUnit.HOURS) // 设置有效期为 1 小时
                            .build()
            );
            logger.info("预签名 URL 已生成: " + presignedUrl);
            return presignedUrl;
        } catch (Exception e) {
            logger.error("文件合并失败", e);
            throw new RuntimeException("文件合并失败", e);
        }
    }
}

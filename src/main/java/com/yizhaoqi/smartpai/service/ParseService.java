package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParseService {

    private static final Logger logger = LoggerFactory.getLogger(ParseService.class);

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Value("${file.parsing.chunk-size}")
    private int chunkSize;

    /**
     * 解析文件并保存文本内容到数据库
     *
     * @param fileMd5 文件的MD5哈希值，用于唯一标识文件
     * @param fileStream 文件输入流，用于读取文件内容
     * @param userId 上传用户ID
     * @param orgTag 组织标签
     * @param isPublic 是否公开
     * @throws IOException 如果文件读取过程中发生错误
     * @throws TikaException 如果文件解析过程中发生错误
     */
    public void parseAndSave(String fileMd5, InputStream fileStream, 
                            String userId, String orgTag, boolean isPublic) throws IOException, TikaException {
        logger.info("开始解析文件，fileMd5: {}, userId: {}, orgTag: {}, isPublic: {}", 
                   fileMd5, userId, orgTag, isPublic);
        // 使用 Apache Tika 提取文档内容
        String textContent = extractText(fileStream);
        // 将文本内容分割为固定大小的块
        List<String> chunks = splitTextIntoChunks(textContent, chunkSize);

        // 保存每个文本块到数据库
        saveChunks(fileMd5, chunks, userId, orgTag, isPublic);

        logger.info("文件解析完成，fileMd5: {}", fileMd5);
    }
    
    /**
     * 兼容旧版本的解析方法
     * @param fileMd5 文件的MD5哈希值
     * @param fileStream 文件输入流
     * @throws IOException 如果文件读取过程中发生错误
     * @throws TikaException 如果文件解析过程中发生错误
     */
    public void parseAndSave(String fileMd5, InputStream fileStream) throws IOException, TikaException {
        // 使用默认值调用新方法
        parseAndSave(fileMd5, fileStream, "unknown", "DEFAULT", false);
    }

    /**
     * 提取文件的文本内容
     *
     * @param fileStream 文件输入流
     * @return 文件的文本内容
     * @throws IOException 如果文件读取过程中发生错误
     * @throws TikaException 如果文件解析过程中发生错误
     */
    private String extractText(InputStream fileStream) throws IOException, TikaException {
        try {
            // 缓存输入流以支持多次读取
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            fileStream.transferTo(baos);
            InputStream cachedStream = new ByteArrayInputStream(baos.toByteArray());

            BodyContentHandler handler = new BodyContentHandler(-1); // 不限制内容长度
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            AutoDetectParser parser = new AutoDetectParser();

            // 解析文件
            parser.parse(cachedStream, handler, metadata, context);

            // 打印元数据
            logger.debug("文件元数据:");
            for (String name : metadata.names()) {
                logger.debug("{}: {}", name, metadata.get(name));
            }

            // 获取解析内容
            String content = handler.toString();
            logger.debug("提取的文本内容: {}", content);

            if (content.isEmpty()) {
                logger.warn("解析结果为空，请检查文件内容或格式");
            }

            return content;
        } catch (org.xml.sax.SAXException e) {
            logger.error("文档解析失败", e);
            throw new RuntimeException("文档解析失败", e);
        }
    }

    /**
     * 将文本内容分割成固定大小的块
     *
     * @param text 原始文本内容
     * @param chunkSize 每个块的大小
     * @return 分割后的文本块列表
     */
    private List<String> splitTextIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            String chunk = text.substring(i, Math.min(i + chunkSize, text.length()));
            chunks.add(chunk);
            logger.debug("文本块: {}", chunk);
        }
        return chunks;
    }

    /**
     * 将文本块保存到数据库
     *
     * @param fileMd5 文件的 MD5 哈希值
     * @param chunks 文本块列表
     * @param userId 上传用户ID
     * @param orgTag 组织标签
     * @param isPublic 是否公开
     */
    private void saveChunks(String fileMd5, List<String> chunks, 
                           String userId, String orgTag, boolean isPublic) {
        for (int i = 0; i < chunks.size(); i++) {
            var vector = new DocumentVector();
            vector.setFileMd5(fileMd5);
            vector.setChunkId(i + 1);
            vector.setTextContent(chunks.get(i));
            vector.setUserId(userId);
            vector.setOrgTag(orgTag);
            vector.setPublic(isPublic);
            documentVectorRepository.save(vector);
        }
    }
    
    /**
     * 兼容旧版本的保存方法
     * @param fileMd5 文件的 MD5 哈希值
     * @param chunks 文本块列表
     */
    private void saveChunks(String fileMd5, List<String> chunks) {
        // 使用默认值调用新方法
        saveChunks(fileMd5, chunks, "unknown", "DEFAULT", false);
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
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
     * @param fileMd5    文件的MD5哈希值，用于唯一标识文件
     * @param fileStream 文件输入流，用于读取文件内容
     * @param userId     上传用户ID
     * @param orgTag     组织标签
     * @param isPublic   是否公开
     * @throws IOException   如果文件读取过程中发生错误
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
     * 
     * @param fileMd5    文件的MD5哈希值
     * @param fileStream 文件输入流
     * @throws IOException   如果文件读取过程中发生错误
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
     * @throws IOException   如果文件读取过程中发生错误
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
     * @param text      原始文本内容
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
     * 智能文本分割，保持语义完整性
     */
    private List<String> splitTextIntoChunksWithSemantics(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = text.split("\n\n+");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            // 如果单个段落超过chunk大小，需要进一步分割
            if (paragraph.length() > chunkSize) {
                // 先保存当前chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }

                // 按句子分割长段落
                List<String> sentenceChunks = splitLongParagraph(paragraph, chunkSize);
                chunks.addAll(sentenceChunks);
            }
            // 如果添加这个段落会超过chunk大小
            else if (currentChunk.length() + paragraph.length() > chunkSize) {
                // 保存当前chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                // 开始新chunk
                currentChunk = new StringBuilder(paragraph);
            }
            // 可以添加到当前chunk
            else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }

        // 添加最后一个chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 分割长段落，按句子边界
     */
    private List<String> splitLongParagraph(String paragraph, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 按句子分割
        String[] sentences = paragraph.split("(?<=[。！？；])|(?<=[.!?;])\\s+");

        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }

                // 如果单个句子太长，按词分割
                if (sentence.length() > chunkSize) {
                    chunks.addAll(splitLongSentence(sentence, chunkSize));
                } else {
                    currentChunk.append(sentence);
                }
            } else {
                currentChunk.append(sentence);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 分割超长句子，按词边界
     */
    private List<String> splitLongSentence(String sentence, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] words = sentence.split("\\s+");

        StringBuilder currentChunk = new StringBuilder();

        for (String word : words) {
            if (currentChunk.length() + word.length() + 1 > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(word);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 使用重叠窗口分割文本
     */
    private List<String> splitTextWithOverlap(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();

        // 首先进行语义分割
        List<String> semanticChunks = splitTextIntoChunks(text, chunkSize);

        // 添加重叠内容
        for (int i = 0; i < semanticChunks.size(); i++) {
            StringBuilder chunkWithOverlap = new StringBuilder();

            // 添加前一个chunk的结尾部分作为重叠
            if (i > 0) {
                String prevChunk = semanticChunks.get(i - 1);
                String prevOverlap = getLastNChars(prevChunk, overlapSize / 2);
                chunkWithOverlap.append(prevOverlap).append(" ");
            }

            // 添加当前chunk
            chunkWithOverlap.append(semanticChunks.get(i));

            // 添加下一个chunk的开头部分作为重叠
            if (i < semanticChunks.size() - 1) {
                String nextChunk = semanticChunks.get(i + 1);
                String nextOverlap = getFirstNChars(nextChunk, overlapSize / 2);
                chunkWithOverlap.append(" ").append(nextOverlap);
            }

            chunks.add(chunkWithOverlap.toString());
        }

        return chunks;
    }

    private String getLastNChars(String text, int n) {
        if (text.length() <= n)
            return text;

        // 在词边界截取
        String substr = text.substring(Math.max(0, text.length() - n));
        int spaceIndex = substr.indexOf(' ');
        return spaceIndex > 0 ? substr.substring(spaceIndex + 1) : substr;
    }

    private String getFirstNChars(String text, int n) {
        if (text.length() <= n)
            return text;

        // 在词边界截取
        String substr = text.substring(0, n);
        int lastSpace = substr.lastIndexOf(' ');
        return lastSpace > 0 ? substr.substring(0, lastSpace) : substr;
    }

    /**
     * 将文本块保存到数据库
     *
     * @param fileMd5  文件的 MD5 哈希值
     * @param chunks   文本块列表
     * @param userId   上传用户ID
     * @param orgTag   组织标签
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
     * 保存chunks时添加关系信息
     */
    private void saveChunksWithSemantics(String fileMd5, List<String> chunks,
            String userId, String orgTag, boolean isPublic) {
        for (int i = 0; i < chunks.size(); i++) {
            var vector = new DocumentVector();
            vector.setFileMd5(fileMd5);
            vector.setChunkId(i + 1);
            vector.setTextContent(chunks.get(i));
            vector.setUserId(userId);
            vector.setOrgTag(orgTag);
            vector.setPublic(isPublic);

            // 添加chunk关系信息
            // vector.setPreviousChunkId(i > 0 ? i : null);
            // vector.setNextChunkId(i < chunks.size() - 1 ? i + 2 : null);
            // vector.setTotalChunks(chunks.size());

            documentVectorRepository.save(vector);
        }
    }

    /**
     * 兼容旧版本的保存方法
     * 
     * @param fileMd5 文件的 MD5 哈希值
     * @param chunks  文本块列表
     */
    private void saveChunks(String fileMd5, List<String> chunks) {
        // 使用默认值调用新方法
        saveChunks(fileMd5, chunks, "unknown", "DEFAULT", false);
    }
}

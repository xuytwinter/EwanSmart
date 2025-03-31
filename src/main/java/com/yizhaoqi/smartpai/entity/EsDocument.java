package com.yizhaoqi.smartpai.entity;


import lombok.Data;

// Elasticsearch存储的文档实体类
@Data
public class EsDocument {

    private String id;             // 文档唯一标识
    private String fileMd5;        // 文件指纹
    private Integer chunkId;       // 文本分块序号
    private String textContent;    // 文本内容
    private float[] vector;        // 向量数据（768维）
    private String modelVersion;   // 向量生成模型版本

    public EsDocument(String string, String fileMd5, int chunkId, String content, float[] floats, String s) {
        this.id = string;
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = content;
        this.vector = floats;
        this.modelVersion = s;
    }
}

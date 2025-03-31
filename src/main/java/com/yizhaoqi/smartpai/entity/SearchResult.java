package com.yizhaoqi.smartpai.entity;

import lombok.Data;

@Data
public class SearchResult {
    private String fileMd5;    // 文件指纹
    private Integer chunkId;   // 文本分块序号
    private String textContent; // 文本内容
    private Double score;      // 搜索得分

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score) {
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = textContent;
        this.score = score;
    }
}

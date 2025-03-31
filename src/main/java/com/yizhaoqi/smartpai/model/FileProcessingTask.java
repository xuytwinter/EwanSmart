package com.yizhaoqi.smartpai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileProcessingTask {
    private String fileMd5; // 文件的 MD5 校验值
    private String filePath; // 文件存储路径
    private String fileName; // 文件名
}

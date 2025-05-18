package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.ParseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/parse")
public class ParseController {

    @Autowired
    private ParseService parseService;

    @PostMapping
    public ResponseEntity<String> parseDocument(@RequestParam("file") MultipartFile file,
                                                @RequestParam("file_md5") String fileMd5) {
        try {
            parseService.parseAndSave(fileMd5, file.getInputStream());
            return ResponseEntity.ok("文档解析成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("文档解析失败：" + e.getMessage());
        }
    }
}
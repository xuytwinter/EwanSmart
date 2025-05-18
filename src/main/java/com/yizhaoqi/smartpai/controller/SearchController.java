package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.entity.SearchRequest;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.List;

// 提供混合检索接口
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private HybridSearchService hybridSearchService;

    /**
     * 混合检索接口
     */
    @PostMapping("/hybrid")
    public List<SearchResult> hybridSearch(@RequestBody SearchRequest request) {
        return hybridSearchService.search(request.getQuery(), request.getTopK());
    }
}

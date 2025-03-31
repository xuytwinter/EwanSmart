package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// 混合检索服务
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    /**
     * 混合检索实现
     * 该方法结合了全文本匹配和向量相似度搜索，以实现更高效的检索
     *
     * @param query 搜索查询字符串
     * @param topK 返回的搜索结果数量
     * @return 返回一个包含搜索结果的列表
     */
    public List<SearchResult> search(String query, int topK) {
        try {
            logger.debug("开始混合检索，查询: {}, topK: {}", query, topK);

            // 生成查询向量
            JsonData queryVector = generateQueryVector(query);
            
            // 如果向量生成失败，仅使用文本匹配
            if (queryVector == null) {
                logger.warn("向量生成失败，仅使用文本匹配进行搜索");
                return textOnlySearch(query, topK);
            }

            SearchResponse<EsDocument> response = esClient.search(s -> s
                    .index("knowledge_base")
                    .query(q -> q
                            .bool(b -> b
                                    .should(sh -> sh
                                            .match(m -> m
                                                    .field("text_content")
                                                    .query(query)
                                            )
                                    )
                                    .should(sh -> sh
                                            .scriptScore(sc -> sc
                                                    .query(qq -> qq.matchAll(ma -> ma))
                                                    .script(script -> script
                                                            .inline(i -> i
                                                                    .source("cosineSimilarity(params.queryVector, 'vector') + 1.0")
                                                                    .params("queryVector", queryVector)
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
                    .size(topK),
                    EsDocument.class
            );

            return response.hits().hits().stream()
                    .map(hit -> {
                        assert hit.source() != null;
                        return new SearchResult(
                                hit.source().getFileMd5(),
                                hit.source().getChunkId(),
                                hit.source().getTextContent(),
                                hit.score()
                        );
                    })
                    .toList();
        } catch (Exception e) {
            logger.error("搜索失败", e);
            // 发生异常时尝试使用纯文本搜索作为后备方案
            try {
                logger.info("尝试使用纯文本搜索作为后备方案");
                return textOnlySearch(query, topK);
            } catch (Exception fallbackError) {
                logger.error("后备搜索也失败", fallbackError);
                throw new RuntimeException("搜索完全失败", fallbackError);
            }
        }
    }

    /**
     * 仅使用文本匹配的搜索方法
     */
    private List<SearchResult> textOnlySearch(String query, int topK) throws Exception {
        SearchResponse<EsDocument> response = esClient.search(s -> s
                .index("knowledge_base")
                .query(q -> q
                        .match(m -> m
                                .field("text_content")
                                .query(query)
                        )
                )
                .size(topK),
                EsDocument.class
        );

        return response.hits().hits().stream()
                .map(hit -> {
                    assert hit.source() != null;
                    return new SearchResult(
                            hit.source().getFileMd5(),
                            hit.source().getChunkId(),
                            hit.source().getTextContent(),
                            hit.score()
                    );
                })
                .toList();
    }

    /**
     * 生成查询向量（调用嵌入模型）
     * 该方法将输入的查询字符串转换为向量表示，以便进行向量相似度搜索。
     * 如果生成向量失败，则返回null。
     *
     * @param query 输入的查询字符串
     * @return 返回查询向量的Json表示，或null表示失败
     */
    private JsonData generateQueryVector(String query) {
        try {
            logger.debug("生成查询向量，查询: {}", query);
            List<float[]> vectors = embeddingClient.embed(List.of(query));
            if (vectors == null || vectors.isEmpty()) {
                logger.warn("生成的向量为空或为null");
                return null;
            }
            return JsonData.of(vectors.get(0));
        } catch (Exception e) {
            logger.error("生成查询向量失败", e);
            return null;
        }
    }
}

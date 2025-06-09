package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 混合检索服务
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EmbeddingClient embeddingClient;
    
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    /**
     * 带权限控制的混合检索实现
     * 结合向量相似度搜索和权限过滤，确保用户只能搜索到有权限访问的文档
     *
     * @param query 搜索查询字符串
     * @param userId 当前用户ID
     * @param topK 返回的搜索结果数量
     * @return 返回一个包含搜索结果的列表
     */
    public List<SearchResult> searchWithPermission(String query, String userId, int topK) {
        try {
            logger.debug("开始带权限的混合检索，查询: {}, 用户: {}, topK: {}", query, userId, topK);

            // 获取用户的有效组织标签（包含父标签关系）
            List<String> userEffectiveTags = getUserEffectiveOrgTags(userId);
            logger.debug("用户有效组织标签: {}", userEffectiveTags);

            // 生成查询向量
            JsonData queryVector = generateQueryVector(query);
            
            // 如果向量生成失败，仅使用文本匹配
            if (queryVector == null) {
                logger.warn("向量生成失败，仅使用文本匹配进行搜索");
                return textOnlySearchWithPermission(query, userId, userEffectiveTags, topK);
            }

            SearchResponse<EsDocument> response = esClient.search(s -> s
                    .index("knowledge_base")
                    .query(q -> q
                            .bool(b -> b
                                    // 匹配内容相关性
                                    .should(sh -> sh
                                            .match(m -> m
                                                    .field("text_content")
                                                    .query(query)
                                            )
                                    )
                                    // 匹配向量相似度
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
                                    // 权限过滤
                                    .filter(f -> f
                                            .bool(bf -> bf
                                                    // 条件1: 用户可以访问自己的文档
                                                    .should(s1 -> s1
                                                            .term(t -> t
                                                                    .field("user_id")
                                                                    .value(userId)
                                                            )
                                                    )
                                                    // 条件2: 用户可以访问公开的文档
                                                    .should(s2 -> s2
                                                            .term(t -> t
                                                                    .field("is_public")
                                                                    .value(true)
                                                            )
                                                    )
                                                    // 条件3: 用户可以访问其所属组织的文档（包含层级关系）
                                                    .should(s3 -> {
                                                        if (userEffectiveTags.isEmpty()) {
                                                            return s3.matchNone(mn -> mn);
                                                        } else if (userEffectiveTags.size() == 1) {
                                                            // 单个标签使用 term 查询
                                                            return s3.term(t -> t
                                                                    .field("org_tag")
                                                                    .value(userEffectiveTags.get(0))
                                                            );
                                                        } else {
                                                            // 多个标签使用 bool should 组合多个 term 查询
                                                            return s3.bool(innerBool -> {
                                                                userEffectiveTags.forEach(tag -> 
                                                                    innerBool.should(sh -> sh.term(t -> t
                                                                            .field("org_tag")
                                                                            .value(tag)
                                                                    ))
                                                                );
                                                                return innerBool;
                                                            });
                                                        }
                                                    })
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
                                hit.score(),
                                hit.source().getUserId(),
                                hit.source().getOrgTag(),
                                hit.source().isPublic()
                        );
                    })
                    .toList();
        } catch (Exception e) {
            logger.error("带权限的搜索失败", e);
            // 发生异常时尝试使用纯文本搜索作为后备方案
            try {
                logger.info("尝试使用纯文本搜索作为后备方案");
                return textOnlySearchWithPermission(query, userId, getUserEffectiveOrgTags(userId), topK);
            } catch (Exception fallbackError) {
                logger.error("后备搜索也失败", fallbackError);
                throw new RuntimeException("搜索完全失败", fallbackError);
            }
        }
    }

    /**
     * 仅使用文本匹配的带权限搜索方法
     */
    private List<SearchResult> textOnlySearchWithPermission(String query, String userId, List<String> userEffectiveTags, int topK) throws Exception {
        SearchResponse<EsDocument> response = esClient.search(s -> s
                .index("knowledge_base")
                .query(q -> q
                        .bool(b -> b
                                // 匹配内容相关性
                                .must(m -> m
                                        .match(mm -> mm
                                                .field("text_content")
                                                .query(query)
                                        )
                                )
                                // 权限过滤
                                .filter(f -> f
                                        .bool(bf -> bf
                                                // 条件1: 用户可以访问自己的文档
                                                .should(s1 -> s1
                                                        .term(t -> t
                                                                .field("user_id")
                                                                .value(userId)
                                                        )
                                                )
                                                // 条件2: 用户可以访问公开的文档
                                                .should(s2 -> s2
                                                        .term(t -> t
                                                                .field("is_public")
                                                                .value(true)
                                                        )
                                                )
                                                // 条件3: 用户可以访问其所属组织的文档（包含层级关系）
                                                .should(s3 -> {
                                                    if (userEffectiveTags.isEmpty()) {
                                                        return s3.matchNone(mn -> mn);
                                                    } else if (userEffectiveTags.size() == 1) {
                                                        // 单个标签使用 term 查询
                                                        return s3.term(t -> t
                                                                .field("org_tag")
                                                                .value(userEffectiveTags.get(0))
                                                        );
                                                    } else {
                                                        // 多个标签使用 bool should 组合多个 term 查询
                                                        return s3.bool(innerBool -> {
                                                            userEffectiveTags.forEach(tag -> 
                                                                innerBool.should(sh -> sh.term(t -> t
                                                                        .field("org_tag")
                                                                        .value(tag)
                                                                ))
                                                            );
                                                            return innerBool;
                                                        });
                                                    }
                                                })
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
                            hit.score(),
                            hit.source().getUserId(),
                            hit.source().getOrgTag(),
                            hit.source().isPublic()
                    );
                })
                .toList();
    }

    /**
     * 原始搜索方法，不包含权限过滤，保留向后兼容性
     */
    public List<SearchResult> search(String query, int topK) {
        try {
            logger.debug("开始混合检索，查询: {}, topK: {}", query, topK);
            logger.warn("使用了没有权限过滤的搜索方法，建议使用 searchWithPermission 方法");

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
    
    /**
     * 获取用户的有效组织标签（包含层级关系）
     */
    private List<String> getUserEffectiveOrgTags(String userId) {
        try {
            // 获取用户名
            User user;
            try {
                Long userIdLong = Long.parseLong(userId);
                user = userRepository.findById(userIdLong)
                    .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
            } catch (NumberFormatException e) {
                // 如果userId不是数字格式，则假设它就是username
                user = userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
            }
            
            // 通过orgTagCacheService获取用户的有效标签集合
            return orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
        } catch (Exception e) {
            logger.error("获取用户有效组织标签失败: {}", e.getMessage(), e);
            return Collections.emptyList(); // 返回空列表作为默认值
        }
    }
}

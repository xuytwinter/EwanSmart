package com.yizhaoqi.smartpai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 组织标签缓存服务
 * 用于缓存用户组织标签信息，提高权限验证效率
 */
@Service
public class OrgTagCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrgTagCacheService.class);
    
    private static final String USER_ORG_TAGS_KEY_PREFIX = "user:org_tags:";
    private static final String USER_PRIMARY_ORG_KEY_PREFIX = "user:primary_org:";
    private static final long CACHE_TTL_HOURS = 24;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 缓存用户的组织标签
     * 
     * @param username 用户名
     * @param orgTags 组织标签列表
     */
    public void cacheUserOrgTags(String username, List<String> orgTags) {
        try {
            String key = USER_ORG_TAGS_KEY_PREFIX + username;
            redisTemplate.opsForList().rightPushAll(key, orgTags.toArray());
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached organization tags for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to cache organization tags for user: {}", username, e);
        }
    }
    
    /**
     * 获取用户的组织标签
     * 
     * @param username 用户名
     * @return 组织标签列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserOrgTags(String username) {
        try {
            String key = USER_ORG_TAGS_KEY_PREFIX + username;
            List<Object> result = redisTemplate.opsForList().range(key, 0, -1);
            if (result != null && !result.isEmpty()) {
                return result.stream()
                        .map(obj -> (String) obj)
                        .toList();
            }
        } catch (Exception e) {
            logger.error("Failed to get organization tags for user: {}", username, e);
        }
        return null;
    }
    
    /**
     * 缓存用户的主组织标签
     * 
     * @param username 用户名
     * @param primaryOrg 主组织标签
     */
    public void cacheUserPrimaryOrg(String username, String primaryOrg) {
        try {
            String key = USER_PRIMARY_ORG_KEY_PREFIX + username;
            redisTemplate.opsForValue().set(key, primaryOrg);
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached primary organization for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to cache primary organization for user: {}", username, e);
        }
    }
    
    /**
     * 获取用户的主组织标签
     * 
     * @param username 用户名
     * @return 主组织标签
     */
    public String getUserPrimaryOrg(String username) {
        try {
            String key = USER_PRIMARY_ORG_KEY_PREFIX + username;
            return (String) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Failed to get primary organization for user: {}", username, e);
            return null;
        }
    }
    
    /**
     * 删除用户的组织标签缓存
     * 
     * @param username 用户名
     */
    public void deleteUserOrgTagsCache(String username) {
        try {
            String orgTagsKey = USER_ORG_TAGS_KEY_PREFIX + username;
            String primaryOrgKey = USER_PRIMARY_ORG_KEY_PREFIX + username;
            redisTemplate.delete(orgTagsKey);
            redisTemplate.delete(primaryOrgKey);
            logger.debug("Deleted organization tags cache for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to delete organization tags cache for user: {}", username, e);
        }
    }
} 
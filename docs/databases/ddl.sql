CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一标识',
                       username VARCHAR(255) NOT NULL UNIQUE COMMENT '用户名，唯一',
                       password VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                       role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '用户角色',
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       INDEX idx_username (username) COMMENT '用户名索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE conversations (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对话记录唯一标识',
                               user_id BIGINT NOT NULL COMMENT '关联用户 ID',
                               question TEXT NOT NULL COMMENT '用户提问内容',
                               answer TEXT NOT NULL COMMENT '系统回答内容',
                               timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间戳',
                               FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                               INDEX idx_user_id (user_id) COMMENT '用户 ID 索引',
                               INDEX idx_timestamp (timestamp) COMMENT '时间戳索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话历史表';

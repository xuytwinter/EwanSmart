import { defineMock } from '@alova/mock'

export default defineMock({
  '[POST]/v1/users/register': () => {
    return {
      code: 200,
      message: 'User registered successfully',
    }
  },
  '[POST]/v1/users/login': () => {
    // throw new Error('Login failed')
    return {
      code: 200,
      message: 'Login successful',
      data: {
        token: 'mock-token',
      },
    }
  },
  '[GET]/v1/users/me': () => {
    return {
      code: 200,
      message: 'User info retrieved successfully',
      data: {
        id: 1,
        username: 'mock-user',
        role: 'USER',
        orgTags: ['dept1', 'team2'],
        primaryOrg: 'dept1',
      },
    }
  },
  '[GET]/v1/admin/users': () => {
    return {
      code: 200,
      message: 'User list retrieved successfully',
      data: {
        content: [
          {
            userId: 'user1',
            username: '用户1',
            email: 'user1@example.com',
            status: 1,
            orgTags: ['dept1', 'team2'],
            primaryOrg: 'dept1',
            createTime: '2023-01-01T12:00:00Z',
            lastLoginTime: '2023-06-15T08:30:00Z',
          },
          {
            userId: 'user2',
            username: '用户2',
            email: 'user2@example.com',
            status: 0,
            orgTags: ['dept1', 'team1'],
            primaryOrg: 'dept1',
            createTime: '2023-01-01T12:00:00Z',
            lastLoginTime: '2023-06-15T08:30:00Z',
          },
        ],
        totalElements: 150,
        totalPages: 8,
        size: 20,
        number: 0,
      },
    }
  },
  '[GET]/v1/admin/org-tags/tree': () => {
    return {
      code: 200,
      message: 'Organization tag retrieved successfully',
      data: [
        {
          tagId: 'dept1',
          name: '部门1',
          description: '部门1描述',
          children: [
            {
              tagId: 'team1',
              name: '团队1',
              description: '团队1描述',
            },
            {
              tagId: 'team2',
              name: '团队2',
              description: '团队2描述',
            },
          ],
        },
        {
          tagId: 'dept2',
          name: '部门2',
          description: '部门2描述',
          children: null,
        },
      ],
    }
  },
  '[POST]/v1/admin/org-tags': () => {
    return {
      code: 200,
      message: 'Organization tag created successfully',
    }
  },
  '[PUT]/v1/admin/org-tags/{tagId}': () => {
    return {
      code: 200,
      message: 'Organization tag updated successfully',
    }
  },
  '[DELETE]/v1/admin/org-tags/{tagId}': () => {
    return {
      code: 200,
      message: 'Organization tag deleted successfully',
    }
  },
  '[PUT]/v1/users/{userId}/org-tags': () => {
    return {
      code: 200,
      message: 'Organization tag assigned successfully',
    }
  },
  '[PUT]/v1/users/primary-org': () => {
    return {
      code: 200,
      message: 'Primary organization updated successfully',
    }
  },
  '[GET]/v1/users/org-tags': () => {
    return {
      code: 200,
      message: 'Get user organization tags successful',
      data: {
        orgTags: ['PRIVATE_example_user', 'dept1', 'team2'],
        primaryOrg: 'PRIVATE_example_user',
        orgTagDetails: [
          {
            tagId: 'PRIVATE_example_user',
            name: 'example_user的私人空间',
            description: '用户的私人组织标签，仅用户本人可访问',
          },
          {
            tagId: 'dept1',
            name: '部门1',
            description: '部门1的组织标签',
          },
          {
            tagId: 'team2',
            name: '团队2',
            description: '团队2的组织标签',
          },
        ],
      },
    }
  },
  '[GET]/v1/admin/files': () => {
    return {
      code: 200,
      message: 'File list retrieved successfully',
      data: {
        content: [
          {
            fileName: '文件1',
            totalSize: 1024,
            status: 1,
            orgTag: 'dept1',
            isPublic: true,
            createdAt: '2023-01-01T12:00:00Z',
          },
          {
            fileName: '文件2',
            totalSize: 2048,
            status: 0,
            orgTag: 'dept2',
            isPublic: false,
            createdAt: '2023-01-02T12:00:00Z',
          },
        ],
        totalElements: 150,
        totalPages: 8,
        size: 20,
        number: 0,
      },
    }
  },
  '[POST]/v1/upload/chunk': () => {
    return {
      code: 200,
      message: 'Chunk uploaded successfully',
      data: {
        uploaded: [0, 1, 2, 3],
        progress: 75.0,
      },
    }
  },
  '[GET]/v1/upload/status': () => {
    return {
      code: 200,
      message: 'Upload status retrieved successfully',
      data: {
        uploaded: [0, 1, 2, 3],
        progress: 75.0,
        total_chunks: 5,
      },
    }
  },
  '[POST]/v1/upload/merge': () => {
    return {
      code: 200,
      message: 'File merged successfully',
      data: {
        object_url: 'https://minio.example.com/reports/年度报告.pdf',
        file_size: 15728640,
      },
    }
  },
  '[DELETE]/v1/documents/{file_md5}': () => {
    return {
      status: 'success',
      message: 'Document deleted successfully',
    }
  },
  '[POST]/search/hybrid': () => {
    return [
      {
        file_md5: 'abc123...',
        chunk_id: 1,
        text_content: '人工智能是未来科技发展的核心方向。',
        score: 0.92,
        file_name: 'AI发展报告.pdf',
      },
    ]
  },
  '[DELETE]/documents/{file_md5}': () => {
    return {
      status: 'success',
      message: 'Document deleted successfully',
    }
  },
}, true)

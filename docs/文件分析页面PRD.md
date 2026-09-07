# File Analysis (文件分析) 模块产品需求文档 (PRD)

---

## 1. 模块概述

### 1.1 业务定位

File Analysis (文件分析) 模块是专为文档质检、模板重构、合同对比、多格式长文本问答与分析打造的工作空间（Document Studio）。系统以物理隔离的隔离子目录（Workspace SubDir）为边界，依托 `ai-tool-doc` SDK 实现多格式文档解析与渲染，结合 PostgreSQL 进行持久化与高频文本缓存，提供沉浸式的 AI 文档处理体验。

### 1.2 核心变更与优化策略

* **去系统化命名**：所有数据库持久化表去除 `sys_` 前缀，采用业务专属前缀 `biz_workspace_*` 与 `biz_session_*`。
* **工作区灵活选择**：支持在 UI 上**切换/选择历史工作区**；若用户未显式选择，系统在发起新对话时自动生成全新的工作区（子目录）。
* **轻量化存储策略**：数据库仅记录文件元数据与 `ai-tool-doc` 预解析的 Markdown 文本（**不存储二进制 Blob**），删除操作由后端服务统一维护“磁盘文件 + 数据库记录”的原子同步。

---

## 2. 页面布局与交互区域设计 (UI/UX)

整体页面采用“左侧双面板（历史工作区 + 当前文件树） + 右侧主对话区 + 独立预览弹窗”的经典布局。

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Header: File Analysis | 当前工作区: /data/workspace/sub_dir_8f3a92/ [切换工作区 ▾]                 │
├───────────────────────────────┬──────────────────────────────────────────────────────────────────┤
│ 左侧边栏 (Width: 300px)       │ 右侧主交互区                                                     │
│ ┌───────────────────────────┐ │ ┌──────────────────────────────────────────────────────────────┐ │
│ │ ➕ 新建工作区(会话)        │ │ │ 消息历史流 (Message List)                                    │ │
│ ├───────────────────────────┤ │ │                                                              │ │
│ │ 🗂️ 历史工作区              │ │ │ [AI]: 已对《季度报告.docx》完成质检，发现 2 处占位符未替换。   │ │
│ │ ├─ 09-07 季度报告质检 [选中]│ │ │       [ 📄 下载质检报告.docx ]  [ 👁️ 查看明细 ]              │ │
│ │ ├─ 09-06 采购合同对比     │ │ └──────────────────────────────────────────────────────────────┘ │
│ │ └─ 09-02 模板设计审查     │ │ ┌──────────────────────────────────────────────────────────────┐ │
│ ├───────────────────────────┤ │ │ 底部 Command 输入框 (Prompt Input & Tool Execution)          │ │
│ │ 📁 文件树 (当前工作区)     │ │ └──────────────────────────────────────────────────────────────┘ │
│ │ ├── 季度报告.docx [👁️][📥][🗑️]│                                                                │
│ │ └── 设计手册.pdf  [👁️][📥][🗑️]│                                                                │
│ │                           │                                                                  │
│ │ [ 📤 上传文件 ]           │                                                                  │
└─┴───────────────────────────┴──────────────────────────────────────────────────────────────────┘
                              ┌──────────────────────────────────────────────────┐
                              │ 浮层弹窗: 轻量化文件预览 Modal (点击 👁️ 触发)     │
                              └──────────────────────────────────────────────────┘

```

---

## 3. 功能详细说明

### 3.1 工作区（子目录）管理逻辑

1. **自动创建**：点击顶部/左侧的“➕ 新建工作区”，系统将在物理磁盘根路径（如 `/data/workspace/`）下自动创建一个 UUID 子目录，并在数据库中写入记录。
2. **切换历史工作区**：点击左侧历史工作区列表或 Header 的下拉菜单，可直接切换到历史工作区。切换后，左侧文件树与右侧对话历史同步刷新为该工作区的数据。
3. **物理与安全边界**：所有后台文件读写、解包、质检指令，均严格限制在当前激活的 `sub_dir_id` 物理路径下，**强行阻断 `../` 路径穿越行为**。

### 3.2 文件管理与轻量化预览

1. **上传文件**：支持用户直接拖拽或点击上传 `.docx`, `.pdf`, `.txt`, `.md`, `.json`, `.png` 等格式。
* 后端写入物理磁盘后，**同步调用 `ai-tool-doc` 提取 Markdown 文本**存入数据库 `parsed_markdown` 字段。


2. **预览机制（小范围白名单）**：
* **DOCX**：点击预览时，直接弹窗展示预解析好的 `parsed_markdown` 视图（轻量、高效，且与 AI 视角完全一致），亦可切换为客户端渲染模式。
* **PDF**：弹窗嵌入原生 PDF Viewer / 流式展示。
* **TXT / MD / JSON**：使用轻量级文本/Markdown 渲染组件展示。
* **不支持格式**：其他格式按钮置灰，提示“暂不支持在线预览，请下载后查看”。


3. **删除操作**：在 UI 上点击 [🗑️ 删除]，后端原子化执行“删除 `biz_workspace_file` 记录 + 删除物理磁盘文件”。

### 3.3 右侧交互与质检产出

* **实时质检卡片**：Agent 发现占位符残留（如 `[请填写预算]`）或数据前后矛盾时，在对话流中直接渲染可视化警告卡片。
* **文件渲染与导出**：Agent 依托 `ai-tool-doc`（基于 `poi-tl`）生成新的分析报告/质检文档，存储至当前工作区，并在前端生成下载按钮卡片。

---

## 4. 数据库持久化设计 (PostgreSQL)

去除了 `sys_` 前缀，采用 `biz_workspace_*` 与 `biz_session_*` 结构。

```sql
-- 1. 工作区（子目录）元数据表
CREATE TABLE biz_workspace_sub_dir (
    sub_dir_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dir_name VARCHAR(255) NOT NULL,         -- 工作区展示名称 (如 "09-07 季度报告质检")
    abs_path VARCHAR(512) NOT NULL,         -- 物理绝对路径 (如 "/data/workspace/sub_dir_uuid/")
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. 文件元数据表 (仅存元数据与解析文本，绝不存二进制 Blob)
CREATE TABLE biz_workspace_file (
    file_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sub_dir_id UUID NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,         -- DOCX, PDF, TXT, MD 等
    file_size BIGINT NOT NULL,
    parsed_markdown TEXT,                   -- ai-tool-doc 预解析出的 Markdown 文本 (加速后续问答)
    parsed_schema JSONB,                    -- 模板文件的变量 Schema
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. 向量索引表 (用于长文档切片召回)
CREATE TABLE biz_workspace_doc_vector (
    vector_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sub_dir_id UUID NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES biz_workspace_file(file_id) ON DELETE CASCADE,
    chunk_content TEXT NOT NULL,            -- 切片内容
    embedding vector(1536),                -- pgvector 向量索引
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 索引：向量加速查询
CREATE INDEX idx_biz_doc_vector_embedding ON biz_workspace_doc_vector USING hnsw (embedding vector_cosine_ops);
-- 索引：文件所属工作区快速查询
CREATE INDEX idx_biz_workspace_file_subdir ON biz_workspace_file(sub_dir_id);

-- 4. 文本分析会话表
CREATE TABLE biz_session_text_analysis (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sub_dir_id UUID NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    context_messages JSONB NOT NULL DEFAULT '[]', -- 对话上下文历史
    execution_trace JSONB DEFAULT '[]',          -- Trace 轨迹记录
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

```

---

## 5. 核心 API 接口设计

### 5.1 工作区与文件 API

| HTTP 方法 | 接口路径 | 描述 |
| --- | --- | --- |
| **GET** | `/api/workspace/list` | 查询历史工作区列表 |
| **POST** | `/api/workspace/create` | 手动/自动新建工作区（创建子目录） |
| **GET** | `/api/workspace/{sub_dir_id}/files` | 获取指定工作区的文件树列表 |
| **POST** | `/api/workspace/{sub_dir_id}/file/upload` | 上传文件（存盘 + `ai-tool-doc` 提取 Markdown 并落库） |
| **GET** | `/api/workspace/file/{file_id}/preview` | 预览文件内容（返回纯文本/Markdown/PDF文件流） |
| **GET** | `/api/workspace/file/{file_id}/download` | 下载原文件或生成的报告 |
| **DELETE** | `/api/workspace/file/{file_id}` | 删除文件（同步清理物理磁盘与数据库记录） |

### 5.2 对话与分析 API (SSE 响应)

| HTTP 方法 | 接口路径 | 描述 |
| --- | --- | --- |
| **POST** | `/api/session/text-analysis/chat` | 发起文件分析对话（返回 SSE 流，调用 `ai-tool-doc` 进行质检/检索/生成） |

---

## 6. 后端防路径穿越与同步服务代码规范

```java
@Service
@Transactional
public class WorkspaceFileService {

    @Value("${workspace.root-path:/data/workspace}")
    private String rootPath;

    @Autowired
    private BizWorkspaceFileRepository fileRepo;

    @Autowired
    private DocParser docParser; // 来自 ai-tool-doc SDK

    /**
     * 安全路径校验，阻断 ../ 路径穿越攻击
     */
    public Path resolveSafePath(UUID subDirId, String fileName) {
        String cleanFileName = Paths.get(fileName).getFileName().toString();
        Path subDirPath = Paths.get(rootPath, subDirId.toString()).normalize().toAbsolutePath();
        Path targetPath = subDirPath.resolve(cleanFileName).normalize().toAbsolutePath();

        if (!targetPath.startsWith(subDirPath)) {
            throw new SecurityException("非法访问拦截: 路径尝试越界 " + fileName);
        }
        return targetPath;
    }

    /**
     * 原子删除文件：同步清空物理磁盘与数据库记录
     */
    public void deleteFile(UUID fileId) {
        BizWorkspaceFile fileMeta = fileRepo.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("目标文件不存在"));

        // 1. 安全计算物理路径并删除
        Path physicalPath = resolveSafePath(fileMeta.getSubDirId(), fileMeta.getFileName());
        try {
            Files.deleteIfExists(physicalPath);
        } catch (IOException e) {
            log.warn("物理磁盘文件删除失败: {}", physicalPath, e);
        }

        // 2. 数据库级联删除元数据及关联向量
        fileRepo.delete(fileMeta);
    }
}

```
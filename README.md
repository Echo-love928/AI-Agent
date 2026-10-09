# AI-Agent

Spring Boot 后端，接入 Spring AI Alibaba 和 DashScope 大模型。

## 本地运行

需要 Java 21。

1. 将 `src/main/resources/application-local.example.yml` 复制为同目录下的 `application-local.yml`。
2. 在本地环境变量中设置 `DASHSCOPE_API_KEY`。
3. 执行 `./mvnw spring-boot:run`；Windows 使用 `.\mvnw.cmd spring-boot:run`。

健康检查地址：`http://localhost:8123/api/health`。

启动时会执行一次大模型调用示例。

## 本地 RAG 向量持久化

首次启动时，知识库加载 `document` 目录下的 Markdown，为每篇文档提取 5 个关键词，再调用 DashScope 嵌入模型生成向量。正文、元信息和向量保存到工作目录下的 `data/mind-vector-store.json`，校验信息保存到旁边的 `mind-vector-store.json.meta.json`。

后续启动会检查当前文档和缓存：校验通过时直接恢复向量，知识库初始化不再调用关键词或嵌入模型。文档增删改、嵌入模型名称或维度、关键词模型名称发生变化，以及缓存损坏时，会自动重建。用户提问时仍需调用嵌入模型转换问题，再检索内存中的向量。

在 `application.yml` 或本地配置中可调整：

- `rag.vector-store.path`：向量文件路径，默认 `data/mind-vector-store.json`；校验文件与其放在同一目录。
- `rag.vector-store.rebuild`：设为 `true` 强制重建，重建完成后改回 `false`。
- `rag.vector-store.cache-version`：修改文档解析或关键词规则后递增，下次启动会重建缓存。

缓存文件通过临时文件替换写入。文档加载或模型调用失败时，初始化中止，保留已有缓存。`data/` 已加入 Git 忽略规则。

## RAG 查询重写

`QueryRewriter.doQueryRewrite(prompt)` 使用 DashScope 聊天模型和 `RewriteQueryTransformer` 将问题重写为适合向量检索的查询。输入不能为空；模型返回空文本时沿用原始问题。

`MindApp.doChatWithRag` 已接入查询重写：使用重写后的文本检索知识库，再将检索到的文档和用户原始问题交给回答模型。会话记忆保存原始问题。每次 RAG 查询增加一次聊天模型调用，普通对话和报告生成沿用原有流程。

查询增强器由 `MindAppContextualQueryAugmenterFactory.createInstance()` 统一创建。有检索结果时补充文档上下文；无结果时使用自定义中文提示词，告知用户知识库暂未找到相关内容，并说明心理树洞支持的场景。`allowEmptyContext(false)` 禁止空上下文直接进入普通回答流程，会话记忆仍保存用户原始问题。

## 配置与隐私

`application-local.yml`、`.env`、日志、IDE 配置和构建产物不提交到仓库。
配置示例只引用环境变量，不包含真实密钥。新增配置前请检查是否包含密钥、密码或个人数据。

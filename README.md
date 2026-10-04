# AI-Agent

Spring Boot 后端，接入 Spring AI Alibaba 和 DashScope 大模型。

## 本地运行

需要 Java 21。

1. 将 `src/main/resources/application-local.example.yml` 复制为同目录下的 `application-local.yml`。
2. 在本地环境变量中设置 `DASHSCOPE_API_KEY`。
3. 执行 `./mvnw spring-boot:run`；Windows 使用 `.\mvnw.cmd spring-boot:run`。

健康检查地址：`http://localhost:8123/api/health`。

启动时会执行一次大模型调用示例。

## 配置与隐私

`application-local.yml`、`.env`、日志、IDE 配置和构建产物不提交到仓库。
配置示例只引用环境变量，不包含真实密钥。新增配置前请检查是否包含密钥、密码或个人数据。

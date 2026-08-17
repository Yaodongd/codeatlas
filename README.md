# CodeAtlas

CodeAtlas 是一个面向开发者的“代码库理解与变更影响分析 Agent”。它会拉取公共 Git 仓库、建立只读文件索引，并通过带文件引用的对话帮助你理解陌生项目。

这是一个完整的前后端部署学习项目：Java 21 + Spring Boot、Vue 3、PostgreSQL、Redis、Nginx、Docker Compose 和 GitHub Actions。

## 能做什么

- 导入 GitHub、GitLab、Gitee、Codeberg 的公共 HTTPS 仓库
- 浅克隆仓库（分支留空时跟随远程默认分支）并提取常见源码、配置和文档文件
- 浏览文件树和源代码
- 使用只读 Agent 工具搜索代码、查找符号、读取文件和生成项目概览
- 回答中返回文件路径引用，避免只有结论、没有证据
- 没有模型 API Key 时自动使用本地索引搜索模式，项目仍可完整运行
- 配置 OpenAI 兼容接口后启用模型工具调用模式

当前版本不会执行被导入仓库中的任何代码。它也不是语义向量检索系统：第一版使用 PostgreSQL 文本索引，后续可把 pgvector 接入为进阶练习。

## 架构

```text
浏览器
  │ /codeatlas/
  ▼
宝塔 Nginx（公网 80 端口）
  │
  ▼ 127.0.0.1:18081
Docker Gateway (Nginx)
  ├── /codeatlas/      → Vue 静态页面
  └── /codeatlas/api/  → Spring Boot API
                              ├── PostgreSQL：项目、文件索引、会话
                              ├── Redis：索引进度缓存
                              └── Git：公共仓库只读浅克隆
```

只有 Gateway 映射到宿主机，而且只监听 `127.0.0.1`。PostgreSQL、Redis 和后端 API 不直接暴露到公网。

## 目录

```text
backend/                 Spring Boot 后端
frontend/                Vue 3 前端
deploy/nginx/            容器内网关配置
deploy/scripts/          服务器自动部署脚本
.github/workflows/       CI/CD 工作流
compose.yaml             生产环境
compose.dev.yaml         本地中间件
```

## 本地开发

需要 Java 21、Maven、Node.js 24 和 Docker Desktop。

### 1. 启动本地数据库和 Redis

```powershell
cd D:\codeatlas
docker compose -f compose.dev.yaml up -d
```

本地端口为 PostgreSQL `55432`、Redis `56379`，只监听本机。

### 2. 启动后端

```powershell
cd D:\codeatlas\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

后端运行在 `http://127.0.0.1:8080`，Flyway 会自动创建数据库表。

### 3. 启动前端

另开一个终端：

```powershell
cd D:\codeatlas\frontend
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`。Vite 会把 `/api` 转发给本地后端。

### 4. 可选：启用 AI 模式

默认不需要模型密钥。需要 AI 对话时，在启动后端前设置：

```powershell
$env:AI_API_KEY="你的密钥"
$env:AI_BASE_URL="https://api.openai.com"
$env:AI_MODEL="gpt-4.1-mini"
```

`AI_BASE_URL` 可替换成兼容 OpenAI Chat Completions 的服务地址。不要把密钥写入 Git。

## 测试和构建

```powershell
cd D:\codeatlas\backend
mvn test

cd D:\codeatlas\frontend
npm ci
npm run build
```

## Docker 部署到服务器

服务器建议至少 2 核、4 GB 内存，并安装 Docker 与 Docker Compose。下面假设目录为 `/www/wwwroot/codeatlas`。

### 1. 上传或克隆代码

```bash
cd /www/wwwroot
git clone 你的仓库地址 codeatlas
cd codeatlas
```

### 2. 创建生产配置

```bash
cp .env.example .env
openssl rand -hex 32
openssl rand -hex 32
chmod 600 .env
```

把两次生成的不同随机值分别填写到 `.env` 的 `POSTGRES_PASSWORD` 和 `REDIS_PASSWORD`。不使用 AI 时保持 `AI_API_KEY=` 为空。

### 3. 构建并启动

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:18081/codeatlas/actuator/health/readiness
```

返回 `UP` 代表后端、PostgreSQL 和 Redis 已联通。应用此时只在服务器本机的 `18081` 端口监听。

### 4. 宝塔 Nginx 配置 IP 子路径

在当前 IP 站点的配置文件中，把下面两段放进已有的 `server { ... }` 内：

```nginx
location = /codeatlas {
    return 301 /codeatlas/;
}

location ^~ /codeatlas/ {
    proxy_pass http://127.0.0.1:18081;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 180s;
}
```

这里 `proxy_pass` 末尾不要添加 `/`，这样 `/codeatlas/` 前缀会完整传给容器网关。保存后重载 Nginx，访问：

```text
http://你的服务器IP/codeatlas/
```

云安全组只需要开放 80（以后配置 HTTPS 时再开放 443）；无需开放 18081、8080、5432 或 6379。

## CI/CD 自动部署

工作流位于 `.github/workflows/ci-cd.yml`。推送到 `master` 后会：

1. 运行后端测试
2. 构建前端
3. 校验 Docker Compose
4. SSH 到服务器，快进更新代码
5. 备份 PostgreSQL、重新构建并启动容器
6. 等待健康检查通过

GitHub 仓库需要配置：

- `DEPLOY_HOST`：服务器公网 IP
- `DEPLOY_USER`：部署用户，例如 `root`
- `DEPLOY_SSH_KEY`：GitHub Actions 使用的 SSH 私钥完整内容
- `DEPLOY_KNOWN_HOSTS`：通过可信网络执行 `ssh-keyscan -H 服务器IP` 得到并核对过指纹的内容

服务器目录必须先完成一次克隆并保留 `.env`。`.env`、数据库卷、仓库数据和备份都不会提交到 Git。

## 常用运维命令

```bash
docker compose ps
docker compose logs -f --tail=100 backend
docker compose restart backend
docker compose pull
docker compose up -d --build
```

停止容器但保留数据：

```bash
docker compose down
```

不要随意执行 `docker compose down -v`，`-v` 会删除数据库与 Redis 数据卷。

## 安全边界

- 仅接受允许列表中的公共 HTTPS Git 地址
- DNS 解析后拒绝回环、内网和链路本地地址，降低 SSRF 风险
- 限制索引文件数量和单文件体积
- 跳过 `.env`、私钥、构建产物和依赖目录
- Agent 工具只读，并绑定当前项目 ID
- 仓库源码从不被执行
- 生产数据库与 Redis 仅存在于 Docker 内部网络

## 下一阶段练习

- 接入 pgvector，实现代码分块与混合检索
- 加入 JavaParser / Tree-sitter，构建符号和调用关系图
- 增加 GitHub OAuth 与私有仓库授权
- 使用对象存储保存索引产物
- 用 K3s 把 Compose 服务迁移为 Deployment、Service、Ingress、Secret 和 PVC

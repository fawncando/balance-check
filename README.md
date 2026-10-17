# 天平校验视频分析系统

第 8 组课程设计。需求见《第8组天平校验视频分析系统项目需求规格说明书》，本仓库当前完成到 **批次 3（框架搭建）**：前后端脚手架、数据库建表、登录与权限、视频上传与存储、天平清单读取。

## 一、目录结构

```text
balance-check/
├─ backend/             Spring Boot 3.3.5 后端
│  ├─ src/main/java/com/bzd/balance/
│  └─ src/main/resources/application.yml
├─ frontend/            Vue 3 + Vite + Element Plus 前端
│  └─ src/{views,layouts,router,stores,api}
├─ database/            schema.sql 建表、seed.sql 初始化数据
├─ docs/                系统设计说明书
├─ testing/             稳定读数段抽取、金标准与识别评测脚本
└─ scripts/             冒烟测试脚本
```

## 二、环境要求

- JDK 17 及以上（本机验证使用 JDK 25）
- MySQL 8.0
- Node.js 20 及以上（本机验证使用 Node 25）
- 无需外网：后端依赖来自本地 Maven 仓库，前端依赖安装完成后可离线运行
- 视频存储默认在 `D:/develop/balance-check-data/uploads`（D 盘剩余 293GB），可用环境变量 `APP_UPLOAD_DIR` 覆盖

## 三、初始化数据库

```powershell
mysql -uroot -p --default-character-set=utf8mb4 < database\schema.sql
mysql -uroot -p --default-character-set=utf8mb4 < database\seed.sql
```

PowerShell 不支持 `<` 重定向，可改用：

```powershell
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < database\schema.sql"
```

数据库连接配置在 `backend/src/main/resources/application.yml`，默认 `root/123456`，库名 `balance_check`。

视频存储路径同样在该文件里配置（`app.upload-dir`）。上传文件按 `年/月/UUID.扩展名` 归档；`/api/system/health` 会返回该目录所在磁盘的总容量、可用容量、使用率和是否可写，便于在内网里监控剩余空间。

## 四、启动

后端（默认 8081 端口）：

```powershell
cd backend
.\mvnw.cmd -o -DskipTests "-Dmaven.repo.local=D:\develop\m2repo" package
java -jar target\balance-check-0.1.0-SNAPSHOT.jar
```

前端（默认 5173 端口，已配置 /api 代理到 8081）：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>。

## 五、演示账号

| 账号 | 姓名 | 角色 | 口令 |
| --- | --- | --- | --- |
| admin | 管理员 | 管理员 | 123456 |
| liyi | 李一 | 检验人员 | 123456 |
| lier | 李二 | 检验人员 | 123456 |
| qiyi | 齐一 | 复核确认人员 | 123456 |

## 六、批次 3 已实现

- 会话登录、退出、当前用户查询；密码 BCrypt 散列存储
- 按角色控制数据范围：检验人员只看自己的视频，复核人员与管理员可看全部
- 视频上传：格式白名单、500MB 限制、按 年/月/UUID 存储、SHA-256 摘要入库
- 视频列表与删除（仅上传者、仅未进入识别流程的视频可删）
- 天平清单读取（含量程、分度值、砝码组与砝码明细）
- 操作日志（登录、退出、上传、删除）
- 前端登录页、检验工作台、天平清单页，记录查询与复核为占位页

## 七、后续批次

| 批次 | 内容 | 当前状态 |
| --- | --- | --- |
| 1 | 需求确认 | 已完成（需求规格说明书 + 需求确认结果） |
| 2 | 方案设计 | 已完成（本仓库 docs/系统设计说明书） |
| 3 | 框架搭建 | 已完成并通过接口实测 |
| 4 | 识别原型 | 待开始（Python 分析服务，输出 JSON） |
| 5 | 记录与判定 | 待开始 |
| 6 | 复核与查询 | 待开始 |
| 7 | 完善优化 | 待开始 |
| 8 | 测试与交付 | 待开始 |

分析服务与后端的接口约定见设计说明书第 5 章，批次 4 直接按该契约实现即可，不需要改动现有表结构。

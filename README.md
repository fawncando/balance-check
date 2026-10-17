# 天平校验视频分析系统

第 8 组课程设计。需求见《第8组天平校验视频分析系统项目需求规格说明书》。

当前进度：批次 1 至 3 已完成，批次 5（记录与判定）已完成，批次 4（识别原型）进行中——稳定读数段与七段数字框已能定位，读数解码尚未达到可用准确率。

## 一、目录结构

```text
balance-check/
├─ backend/       Spring Boot 3.3.5 后端（登录、上传、天平清单、记录与判定）
├─ frontend/      Vue 3 + Vite + Element Plus 前端
├─ analysis/      Python 识别原型（OpenCV 七段码定位与解码、稳定读数段）
├─ database/      schema.sql 建表、seed.sql 初始化数据
├─ docs/          系统设计说明书、运行截图
├─ testing/       金标准标注与识别评测脚本
└─ scripts/       冒烟测试（批次 3、批次 5）
```

## 二、环境要求

- JDK 17 及以上（本机验证使用 JDK 25）
- MySQL 8.0
- Node.js 20 及以上（本机验证使用 Node 25）
- Python 3.11 及以上（识别原型使用项目内 `.venv`）
- ffmpeg（识别抽帧用，可用环境变量 `FFMPEG` 指定）
- 视频存储默认在 `D:/develop/balance-check-data/uploads`，可用 `APP_UPLOAD_DIR` 覆盖
- 后端依赖来自本地 Maven 仓库，前端与识别依赖安装完成后可离线运行

## 三、初始化数据库

```powershell
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < database\schema.sql"
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < database\seed.sql"
```

数据库连接配置在 `backend/src/main/resources/application.yml`，默认 `root/123456`，库名 `balance_check`。`/api/system/health` 会返回存储路径、磁盘容量与可写状态，便于监控。

## 四、启动

后端（8081）：

```powershell
cd backend
.\mvnw.cmd -o -DskipTests "-Dmaven.repo.local=D:\develop\m2repo" package
java -jar target\balance-check-0.1.0-SNAPSHOT.jar
```

前端（5173，已配置 /api 代理到 8081）：

```powershell
cd frontend
npm install
npm run dev
```

识别原型（可选）：

```powershell
cd analysis
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe seven_seg.py <帧图片> --debug
```

浏览器访问 <http://localhost:5173>。

## 五、演示账号

| 账号 | 姓名 | 角色 | 口令 |
| --- | --- | --- | --- |
| admin | 管理员 | 管理员 | 123456 |
| liyi | 李一 | 检验人员 | 123456 |
| lier | 李二 | 检验人员 | 123456 |
| qiyi | 齐一 | 复核确认人员 | 123456 |

## 六、已实现功能

### 6.1 账号与权限（批次 3）

会话登录、退出、当前用户查询；密码 BCrypt 散列；检验人员只能看到自己的视频，复核人员与管理员可看全部；复核人员不能创建或修改校验记录。

### 6.2 视频上传与存储（批次 3）

格式白名单、500MB 限制、按 `年/月/UUID.扩展名` 存储、SHA-256 摘要入库；支持列表与删除（仅上传者、仅未进入识别流程的视频）。

### 6.3 天平清单（批次 3）

按清单带出科室、唯一号、量程、分度值、砝码组与标准砝码；F001 为 220 g / 0.1 mg（200/50/10 g），F002 为 120 g / 0.1 mg 与 40 g / 0.01 mg（100/20/2 g）。

### 6.4 记录与判定（批次 5）

- 新建校验记录：按天平自动生成九次称量行，填写示值、单位与测量时间，录入清洁、水平调节、内部校正与备注
- 服务端判定：限度按标称值 ±0.05%，mg 自动换算为 g，小数位由分度值决定；任一次超差则整机不通过
- 草稿可修改，提交后状态变为 SUBMITTED 并锁定，不能再编辑
- 记录列表支持按编号、天平、状态筛选；详情页展示九次明细、限度与判定

### 6.5 识别原型（批次 4，进行中）

ffmpeg 抽帧 + 稳定读数段检测已在样本视频上跑通；OpenCV 能定位显示屏中的七段数字框，0.0000 帧上 5 个数字框有 4 个解码正确。称量帧的多位数字配对与单位识别仍需校准。

## 七、接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录 |
| POST | /api/auth/logout | 退出 |
| GET | /api/auth/me | 当前用户 |
| GET | /api/balances | 天平清单（含量程与砝码） |
| POST | /api/videos | 上传视频 |
| GET | /api/videos | 视频列表 |
| DELETE | /api/videos/{id} | 删除未识别的视频 |
| POST | /api/calibrations | 创建校验记录草稿 |
| PUT | /api/calibrations/{id} | 修改草稿 |
| POST | /api/calibrations/{id}/submit | 提交复核 |
| GET | /api/calibrations | 记录列表 |
| GET | /api/calibrations/{id} | 记录详情 |
| GET | /api/system/health | 健康检查与存储容量 |

## 八、测试

```powershell
pwsh -File scripts\smoke-test.ps1   # 批次 3：登录、权限、天平、上传（16 项）
pwsh -File scripts\batch5-test.ps1  # 批次 5：判定、草稿、提交锁定、双量程（13 项）
```

浏览器端另有 8 项 Playwright 检查（九行生成、实时判定、提交、详情、列表、控制台），截图在 `docs/screenshots`。

已知问题：本机 Maven 的 testCompile 无法解析主代码包，判定逻辑暂用独立 runner 验证，JUnit 测试待恢复；批次 4 的读数解码准确率尚未达标。

## 九、批次进度

| 批次 | 内容 | 当前状态 |
| --- | --- | --- |
| 1 | 需求确认 | 已完成（需求规格说明书、需求确认结果） |
| 2 | 方案设计 | 已完成（系统设计说明书） |
| 3 | 框架搭建 | 已完成并通过接口实测 |
| 4 | 识别原型 | 进行中（稳定段与七段定位可用，解码待校准） |
| 5 | 记录与判定 | 已完成（前后端联调通过） |
| 6 | 复核与查询 | 待开始 |
| 7 | 完善优化 | 待开始 |
| 8 | 测试与交付 | 待开始 |

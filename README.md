# 天平校验视频分析系统

第 8 组课程设计。需求见《第8组天平校验视频分析系统项目需求规格说明书》。

当前进度：批次 1 至 8 均已完成。视频上传后可自动识别九次称量、换算 g/mg、生成校验草稿并进入人工确认流程。

## 一、目录结构

```text
balance-check/
├─ backend/       Spring Boot 3.3.5 后端（登录、上传、天平清单、记录与判定、复核、基础数据维护）
├─ frontend/      Vue 3 + Vite + Element Plus 前端
├─ analysis/      Python 视频识别（稳定段、字符模板、g/mg 与小数点规则）
├─ database/      schema.sql 建表、seed.sql 初始化数据
├─ docs/          设计说明书、部署手册、测试报告、交付清单、运行截图
├─ testing/       金标准标注与识别评测脚本
├─ scripts/       冒烟测试、独立校验、环境检查、建库、启停、打包与交付校验
└─ dist/          交付包（构建产物，不入库）
```

## 二、环境要求

- JDK 17 及以上（本机验证使用 JDK 25）
- MySQL 8.0
- Node.js 20 及以上（本机验证使用 Node 25）
- Python 3.11 及以上（识别原型使用项目内 `.venv`）
- ffmpeg（视频抽帧必需；Windows 推荐执行 `pwsh -File scripts\install-ffmpeg.ps1` 安装到 `D:\develop\ffmpeg`）
- ffprobe（上传时验证真实视频流；与 ffmpeg 安装在同一目录）
- 视频存储默认在 `D:/develop/balance-check-data/uploads`，可用 `APP_UPLOAD_DIR` 覆盖
- 后端依赖来自本地 Maven 仓库，前端与识别依赖安装完成后可离线运行

当前机器缺少 ffmpeg 时，可执行：

```powershell
pwsh -File scripts\install-ffmpeg.ps1
```

脚本会下载官方稳定构建、校验 SHA-256，并把 `ffmpeg.exe`、`ffprobe.exe` 安装到
`D:\develop\ffmpeg\bin`。`start-backend.ps1` 会自动使用该路径；也可用
`APP_RECOGNITION_FFMPEG` 指定自定义位置。识别脚本另有 OpenCV 抽帧兜底。

## 三、初始化数据库

```powershell
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < database\schema.sql"
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < database\seed.sql"
```

数据库连接配置在 `backend/src/main/resources/application.yml`，运行时必须提供独立的 `DB_USERNAME` / `DB_PASSWORD`，库名 `balance_check`。`/api/system/health` 只返回磁盘容量与可写状态，不暴露服务器目录。

## 四、启动

后端（8081）：

```powershell
cd backend
.\mvnw.cmd -DskipTests "-Dmaven.repo.local=D:\develop\m2repo" package
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
| admin | 管理员 | 管理员 | 部署密钥 |
| liyi | 李一 | 检验人员 | 部署密钥 |
| lier | 李二 | 检验人员 | 部署密钥 |
| qiyi | 齐一 | 复核确认人员 | 部署密钥 |

## 六、已实现功能

### 6.1 账号与权限（批次 3）

会话登录、退出、当前用户查询；密码 BCrypt 散列；检验人员只能看到自己的视频，复核人员与管理员可看全部；复核人员不能创建或修改校验记录。

### 6.2 视频上传与存储（批次 3）

格式白名单、1GB 限制、按 `年/月/UUID.扩展名` 存储、SHA-256 摘要入库；支持列表与删除（仅上传者、仅未进入识别流程的视频）。

### 6.3 天平清单（批次 3）

按清单带出科室、唯一号、量程、分度值、砝码组与标准砝码；F001 为 220 g / 0.1 mg（200/50/10 g，支持 g/mg 显示），F002 为 120 g / 0.1 mg 与 40 g / 0.01 mg（100/20/2 g，兼容原机位和新机位），F003 为 220 g / 0.1 mg（200/50/10 g，黑底白字）。F004、F005 为新增图形屏设备，使用通用 OCR；F004 唯一号为待现场补充项。

### 6.4 记录与判定（批次 5）

- 新建校验记录：按天平自动生成九次称量行，填写示值、单位与测量时间，录入清洁、水平调节、内部校正与备注
- 服务端判定：限度按标称值 ±0.05%，mg 自动换算为 g，小数位由分度值决定；任一次超差则整机不通过
- 草稿可修改，提交后状态变为 SUBMITTED 并锁定，不能再编辑
- 记录列表支持按编号、天平、状态筛选；详情页展示九次明细、限度与判定

### 6.5 复核与查询（批次 6）

- 复核人员对待复核记录作出通过或退回结论：通过后状态变为 REVIEWED 并锁定，退回则回到 DRAFT 并保留退回意见
- 退回必须填写复核意见，通过可不填；复核结论一条记录一条，重复复核覆盖上一条，历次动作写入 audit_log
- 已复核记录可由复核人员解锁，记录回到草稿，检验人员修改后重新提交
- 记录查询支持按天平、状态、记录编号关键字与日期段过滤；传 `pageSize` 时按页返回，总数在 `X-Total-Count` 响应头，前端带分页控件；检验人员只能查询与查看自己创建的记录
- 复核人员与管理员可直接下载服务端生成的 A4 PDF；保留 HTML 打印页作为兼容入口
- 记录详情展示复核结论、复核意见与九次称量偏差曲线（相对标称值的百分比，允许带 ±0.05%）
- 管理员可填写原因作废记录；作废后保留九次数据和审计日志，并永久禁止修改、提交、复核和解锁

### 6.6 完善优化（批次 7）

- 天平清单维护：复核确认人员与管理员可新增、编辑天平，维护量程上限、分度值与砝码组，停用后不再出现在新建记录的备选清单
- 操作日志：管理员按操作类型、操作人与日期段分页查询 audit_log，日志带出操作人姓名
- 记录表模板：管理员按名称维护多个版本，同一模板同一时间只启用一个版本，启用新版本会自动停用旧版本
- 人员与权限：管理员新增登录账号、自定义姓名与初始密码、分配角色、修改权限、重置密码、启停或删除账号，并维护唯一人员卡二维码；删除后历史记录和审计仍保留
- 侧栏菜单按角色过滤：新建记录只对检验人员与管理员显示，记录表模板与操作日志只对管理员显示

### 6.7 测试与交付（批次 8）

- 后端配置外部化：端口、数据库连接、上传目录、上传上限都可用环境变量覆盖，换机器不必改文件
- 新增运维脚本：环境检查、数据库初始化、后端启停（含健康检查等待与日志落盘）
- 新增交付脚本：一键打包（后端 jar + 前端 dist + SQL + 文档 + 脚本 + VERSION）与交付包校验
- 新增一键测试 `scripts/test-all.ps1`：后端编译、两套独立校验、前端单元测试，后端在线时自动追加接口冒烟
- 补齐交付文档：部署手册、测试报告、交付清单；全部 PowerShell 脚本改为 UTF-8 BOM，Windows PowerShell 5.1 可直接执行

### 6.8 视频识别与自动建单（批次 4）

- 上传成功后前端自动调用 `POST /api/videos/{id}/analyze`，状态按 `UPLOADED → ANALYZING → ANALYZED/FAILED` 推进
- 识别任务进入后台线程池，接口约 100 ms 返回 `ANALYZING`，前端每秒轮询视频状态
- 检验工作台显示手机上传入口二维码；手机打开 `/mobile` 专用页面，选择或拍摄视频后直接传到电脑
- 识别成功后默认删除源视频并清空存储路径（`APP_VIDEO_DELETE_SOURCE=false` 可保留作证据），清理后工作台隐藏“重新识别”，记录与 PDF 不受影响
- 记录列表支持后端分页：`page`/`pageSize` 控制分页，响应头 `X-Total-Count` 返回总数
- 上传时记录视频实际拍摄开始时间，测量时间按“拍摄时间 + 视频秒数”计算
- 工作台和手机最近上传列表自动轮询，不再需要手工刷新
- 上传记录状态跟随校验记录显示为待确认、待复核、已复核或已作废
- 被退回的记录在工作台直接显示退回原因，进入编辑页也会保留复核意见提示
- 检验工作台、复核工作台和记录查询在窄窗口隐藏非关键列并压缩侧栏，避免横向滚动
- 列表时间统一按两行显示：第一行日期，第二行时分秒
- 校验记录查询和复核工作台在页面可见时每 4 秒自动刷新
- 复核工作台精简为记录、检验人员、提交时间、结论、状态和操作
- 从检验工作台提交复核后自动返回工作台；记录查询和复核台进入详情后会返回原页面
- 后端重启时将遗留的 `ANALYZING` 任务标记为失败，可重新识别
- 手工补录必须填写原因，备注和审计日志保留补录说明
- F001 自动比较 `f001` 与 `f001_mg`；F002 自动比较 `f002a`、`f002b` 与 `f002c`；F003 使用 `f003`；未知普通图形屏自动使用 RapidOCR `generic` 档案
- F002 读数数量相同时优先保留检测到 `mg` 的档案，示值单位与换算值分别保存
- 手机不同录制分辨率会先归一化到模板基准 `544×960`，再裁剪显示屏区域
- 固定区域不足九次时自动搜索显示屏位置和缩放比例，原有样例仍优先走固定标定
- 设备与候选档案通过 `app.recognition.device-profiles` 配置，新增设备只需增加模板、`profiles.json` 和映射
- 使用 OpenCV QRCodeDetector 识别视频中人员卡；卡号与上传账号 `card_no` 一致才标记通过，出现不一致时识别失败且不建单
- 输出整体/单次置信度与异常帧数；低于阈值或异常帧过多时标记“需重点复核”
- 自动识别 g/mg、小数点、量程、视频时间点，并生成九次称量草稿
- 草稿关联原视频，检验人员确认清洁、水平调节与内部校正信息后可提交复核
- 三段样本视频已通过真实后端接口完成端到端测试，均生成 9 条称量并判定通过

## 七、接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录 |
| POST | /api/auth/logout | 退出 |
| GET | /api/auth/me | 当前用户 |
| GET | /api/balances | 天平清单（含量程与砝码） |
| POST | /api/balances | 新增天平（复核人员、管理员） |
| PUT | /api/balances/{id} | 修改天平与量程、启用停用（复核人员、管理员） |
| GET | /api/weight-sets | 砝码组与盒内砝码 |
| POST | /api/videos | 上传视频 |
| GET | /api/videos | 视频列表 |
| POST | /api/videos/{id}/analyze | 识别视频并生成校验草稿 |
| GET | /api/videos/{id} | 查询识别状态与身份核验结果 |
| DELETE | /api/videos/{id} | 删除未识别的视频 |
| POST | /api/calibrations | 创建校验记录草稿 |
| PUT | /api/calibrations/{id} | 修改草稿 |
| POST | /api/calibrations/{id}/submit | 提交复核 |
| POST | /api/calibrations/{id}/review | 复核通过或退回（复核人员） |
| POST | /api/calibrations/{id}/reopen | 解锁已复核记录（复核人员） |
| POST | /api/calibrations/{id}/void | 填写原因作废记录（管理员） |
| GET | /api/calibrations | 记录查询（balanceId、status、recordNo、dateFrom、dateTo、page、pageSize；总数见 X-Total-Count） |
| GET | /api/calibrations/{id} | 记录详情 |
| GET | /api/calibrations/export?id= | 记录表打印页（HTML，复核人员） |
| GET | /api/calibrations/{id}/pdf | 服务端生成 A4 PDF（复核人员、管理员） |
| GET | /api/logs | 操作日志（管理员，分页） |
| GET | /api/users | 用户列表（管理员） |
| PUT | /api/users/{id}/card | 绑定或替换人员卡号（管理员） |
| DELETE | /api/users/{id}/card | 解绑人员卡（管理员） |
| GET | /api/users/{id}/card/qr | 下载人员卡二维码（管理员） |
| GET | /api/templates | 记录表模板版本（管理员） |
| POST | /api/templates | 新增模板版本（管理员） |
| PUT | /api/templates/{id}/enabled | 启用或停用模板版本（管理员） |
| GET | /api/system/health | 健康检查与存储容量 |
| GET | /api/system/mobile-entry | 手机上传局域网地址 |
| GET | /api/system/mobile-entry/qr | 手机入口二维码 PNG |

## 八、测试

```powershell
pwsh -File scripts\test-all.ps1            # 一键：后端编译 + 独立校验 + 前端测试（后端在线时含接口冒烟）
pwsh -File scripts\smoke-test.ps1          # 批次 3：登录、权限、CORS、Cookie、会话与上传（28 项）
pwsh -File scripts\batch5-test.ps1         # 批次 5：判定、草稿、提交锁定、双量程（14 项）
pwsh -File scripts\batch6-test.ps1         # 批次 6：复核、退回、解锁、查询、分页、导出（40 项）
pwsh -File scripts\batch6-unit-check.ps1   # 复核/作废规则、记录表渲染与识别失败文案（22 项）
pwsh -File scripts\void-test.ps1           # 作废权限、原因、锁定、查询与审计（13 项）
pwsh -File scripts\batch7-test.ps1         # 批次 7：清单维护、操作日志、模板、权限（36 项）
pwsh -File scripts\batch7-unit-check.ps1   # 天平清单与局域网地址（13 项，不依赖数据库）
pwsh -File scripts\user-card-test.ps1      # 人员卡权限、唯一性、二维码、解绑与审计（12 项）
pwsh -File scripts\user-management-test.ps1 # 人员新增、角色、密码、会话失效、删除与审计（32 项）
analysis\.venv\Scripts\python.exe -m unittest discover -s analysis -p "test_*.py" -v
                                           # 小数点、g/mg、设备档案、人员 QR 与手机入口 QR（8 项）
analysis\.venv\Scripts\python.exe testing\validate_recognition.py --video-dir D:\videos --ffmpeg D:\tools\ffmpeg.exe
                                           # 三段样本视频识别回归（3 个用例）
node --test frontend/src/utils/judgement.test.mjs frontend/src/utils/review.test.mjs frontend/src/utils/device.test.mjs
                                          # 前端换算、复核/作废状态与移动端/角色路由（19 项）
```

浏览器端可用 `scripts/browser-acceptance.ps1` 连接本机 Edge 调试端口，验证登录、上传、
自动识别、九行草稿、提交复核和记录查询，并更新 `docs/screenshots`。本次真实浏览器
验收已通过。

交付相关脚本：

```powershell
pwsh -File scripts\check-environment.ps1                    # 环境检查（JDK/MySQL/端口/磁盘）
pwsh -File scripts\init-database.ps1                        # 建库与初始化数据
pwsh -File scripts\start-backend.ps1                       # 启动后端并等待健康检查
pwsh -File scripts\stop-backend.ps1                        # 停止后端
pwsh -File scripts\backup.ps1 -Password <数据库口令> -IncludeUploads  # 备份数据库与视频
pwsh -File scripts\package-release.ps1                      # 生成 dist/balance-check-<版本> 与 zip
pwsh -File scripts\verify-release.ps1 -Path dist\balance-check-0.1.0-SNAPSHOT
pwsh -File scripts\stress-test.ps1 -BaseUrl http://localhost:8081 -VideoDir D:\videos
                                           # 3 路并发识别 + 6 路并发提交/复核
pwsh -File scripts\mobile-acceptance.ps1 -Port 9222 -AppUrl http://<电脑IP>:5175
                                           # 移动视口 + 局域网地址验收
```

已知限制：`mvnw test` 已可执行，但后端规则仍主要由独立 runner 覆盖；普通图形屏可使用通用 OCR，七段数码管仍建议建立专用模板以保证精度。当前仍为单实例内存 Session，多实例部署需要共享会话。

## 九、批次进度

| 批次 | 内容 | 当前状态 |
| --- | --- | --- |
| 1 | 需求确认 | 已完成（需求规格说明书、需求确认结果） |
| 2 | 方案设计 | 已完成（系统设计说明书） |
| 3 | 框架搭建 | 已完成并通过接口实测 |
| 4 | 视频识别 | 已完成（异步识别、人员卡、置信度、自动建单与端到端测试） |
| 5 | 记录与判定 | 已完成（前后端联调通过） |
| 6 | 复核与查询 | 已完成（复核、退回、解锁、查询、记录表导出） |
| 7 | 完善优化 | 已完成（清单维护、操作日志、记录表模板） |
| 8 | 测试与交付 | 已完成（配置外部化、运维脚本、交付文档与一键测试） |

## 十、交付与部署

部署步骤、环境变量、Nginx 配置与常见问题见 [部署手册](docs/部署手册.md)；测试范围、用例统计与缺陷记录见 [测试报告](docs/测试报告.md)；验收步骤与已知限制见 [交付清单](docs/交付清单.md)。

最快路径：

```powershell
pwsh -File scripts\check-environment.ps1     # 1. 环境检查
pwsh -File scripts\init-database.ps1         # 2. 建库与初始化数据
pwsh -File scripts\start-backend.ps1         # 3. 启动后端并做健康检查
pwsh -File scripts\test-all.ps1              # 4. 一键测试（接口组需后端在线）
```

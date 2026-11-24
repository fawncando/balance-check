# 天平校验视频识别（批次 4）

输入一段校验视频，输出九次称量读数和整机判定。后端通过
`POST /api/videos/{id}/analyze` 自动调用本目录入口并生成校验草稿。

## 一、安装依赖

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

正式部署建议安装 ffmpeg，可通过 `--ffmpeg`、`APP_RECOGNITION_FFMPEG` 或
环境变量 `FFMPEG` 指定路径。Windows 可在项目根目录执行
`pwsh -File scripts\install-ffmpeg.ps1` 安装到 `D:\develop\ffmpeg\bin`。
未安装 ffmpeg 时会自动使用 OpenCV 解码兜底，但处理长视频时 ffmpeg 通常更快。
上传接口另使用同目录的 `ffprobe.exe` 验证文件确实包含视频流和有效时长。

## 二、运行

```powershell
python analyze_video.py `
  --video D:\videos\Balance_1_second.mp4 `
  --profile f002b `
  --ffmpeg D:\tools\ffmpeg.exe `
  --out result.json
```

七套显示档案：

| profile | 设备 | 显示类型 |
| --- | --- | --- |
| generic | 未知普通图形屏设备 | RapidOCR 通用文字识别，按数据库砝码校验 |
| f001 | F001（220 g，200/50/10 g） | Mettler Toledo 图形屏 |
| f001_mg | F001（同上，显示单位为 mg） | Mettler Toledo 图形屏低机位 |
| f002a | F002（120 g 量程，100 g；40 g 量程，20/2 g） | Sartorius 七段屏，显示 g |
| f002b | F002（同上，第二量程显示 mg） | Sartorius 七段屏，g + mg |
| f002c | F002（同类新机位，g/mg 自动判定） | Sartorius 七段屏，完整数字模板 + 原生分辨率 OCR 校正 |
| f003 | F003（220 g，200/50/10 g） | Sartorius 七段屏，黑底白字 |

后端选择 F002 时会比较 `f002a`、`f002b` 与 `f002c`，采用能够解出更多完整称量的结果。

设备档案定义在 `profiles.json`。新增设备时：

1. 按标定截图生成新的 `.npz` 字符模板；
2. 在 `profiles.json` 增加模板、标称砝码与解码规则；
3. 在后端 `app.recognition.device-profiles` 中把天平编号映射到候选档案。

黑底白字等不同极性和固定区域可通过档案中的 `light_digits`、`threshold_value`
与 `display_roi` 配置。`build_sartorius_template.py` 可用已标注读数的截图生成模板。

`generic` 档案使用 RapidOCR + ONNX Runtime 对完整画面做文字检测，自动尝试
0°、90°、180°、270° 方向，过滤日期、菜单、百分比和最大值等次要数字，并用后端传入的
标准砝码数值判断主读数及 `g/mg`。七段数码管仍优先使用专用模板，精度和速度更高。

## 三、小数点与单位规则

`f002b` 的六位数字串存在显示歧义：

- `20000.1 mg` 与 `2000.08 mg` 的数字串都是六位；
- 小数点在画面中的位置不同，但小连通域检测容易受到其他标记干扰；
- 显示屏采用右对齐，`20000.x mg` 的数字区起点约为 x=56，
  `2000.0x mg` 的起点约为 x=86。

因此该档案用数字区左边缘区分两种量程，再统一换算为 g。该规则不依赖
称量顺序，适合固定机位拍摄。

识别 JSON 的每条称量同时输出 `displayUnit`，保留屏幕实际显示的 `g` 或
`mg`；后端另存换算后的 `valueG` 用于限度和判定。

## 四、稳定段与判定

- 按 1 fps 抽帧并裁剪显示屏区域。
- 手机视频先按模板参考尺寸 `544×960` 归一化，兼容常见的 `1080×1920` 录制分辨率。
- 固定显示区域不足九次时，会在画面内搜索多个位置和缩放比例，选择读数接近已知砝码且模板置信度最高的区域。
- 同一读数持续不少于 3 秒才进入候选稳定段。
- 同一称量内末位跳动的相邻稳定段在 5 秒内合并。
- 每个标称砝码选取最稳定的三个有效段，共输出九次称量。
- 单次限度为标称值的 ±0.05%，任一次超差则整机 FAIL。
- 每次字符匹配计算模板 MSE 与次优模板差值，输出 0-100 的置信度。
- 过渡帧、去皮帧允许无法解码；异常帧超过总帧数 80% 或整体置信度低于
  阈值时，结果标记 `needsReview=true`。

## 五、人员卡识别

抽帧使用完整画面，除显示区域外的其他区域也会扫描 QR 码。识别出的
`personCard` 由后端与上传账号的 `card_no` 比对：

- 卡号一致：身份核验通过；
- 卡号不一致：视频识别失败，不生成校验记录；
- 未检测到卡：允许生成草稿，但页面提示人工核验身份。

## 六、已验证样本

三条样本视频均输出九次读数并判定 PASS：

- Balance_2：200.0009/200.0000/200.0009、
  50.0003/50.0002/50.0003、10.0001×3
- Balance_1_first：100.0004×3、20.0002×3、2.0001×3
- Balance_1_second：100.0003/100.0003/100.0008、
  20.0002/20.0001/20.0001、2.00008/2.00006/2.00008

新增 F003 黑底白字样本 `VID_20261124_163741.mp4` 也输出九次读数并判定 PASS：

- 199.9932/199.9933/199.9933 g
- 49.9984/49.9985/49.9984 g
- 9.9997/9.9997/9.9998 g

单元测试：

```powershell
python -m unittest discover -s analysis -p "test_*.py" -v
```

三段视频回归测试：

```powershell
python testing\validate_recognition.py --video-dir D:\videos --ffmpeg D:\tools\ffmpeg.exe
```

## 七、限制

- 当前覆盖 F001、F002 的三段固定机位样本和一条 F003 手机样本，新增机位仍需要重新标定模板。
- 人员二维码/人员卡识别已实现，识别结果由后端与登录账号卡号比对。
- 后端当前以本机进程同步调用识别入口；多人并发场景建议改为任务队列。

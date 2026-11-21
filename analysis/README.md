# 天平校验视频识别（批次 4）

输入一段校验视频，输出九次称量读数和整机判定。后端通过
`POST /api/videos/{id}/analyze` 自动调用本目录入口并生成校验草稿。

## 一、安装依赖

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

另需 ffmpeg，可通过 `--ffmpeg`、`APP_RECOGNITION_FFMPEG` 或环境变量
`FFMPEG` 指定路径。

## 二、运行

```powershell
python analyze_video.py `
  --video D:\videos\Balance_1_second.mp4 `
  --profile f002b `
  --ffmpeg D:\tools\ffmpeg.exe `
  --out result.json
```

三个显示档案：

| profile | 设备 | 显示类型 |
| --- | --- | --- |
| f001 | F001（220 g，200/50/10 g） | Mettler Toledo 图形屏 |
| f002a | F002（120 g 量程，100 g；40 g 量程，20/2 g） | Sartorius 七段屏，显示 g |
| f002b | F002（同上，第二量程显示 mg） | Sartorius 七段屏，g + mg |

后端选择 F002 时会比较 `f002a` 与 `f002b`，采用能够解出更多完整称量的
结果。

设备档案定义在 `profiles.json`。新增设备时：

1. 按标定截图生成新的 `.npz` 字符模板；
2. 在 `profiles.json` 增加模板、标称砝码与解码规则；
3. 在后端 `app.recognition.device-profiles` 中把天平编号映射到候选档案。

## 三、小数点与单位规则

`f002b` 的六位数字串存在显示歧义：

- `20000.1 mg` 与 `2000.08 mg` 的数字串都是六位；
- 小数点在画面中的位置不同，但小连通域检测容易受到其他标记干扰；
- 显示屏采用右对齐，`20000.x mg` 的数字区起点约为 x=56，
  `2000.0x mg` 的起点约为 x=86。

因此该档案用数字区左边缘区分两种量程，再统一换算为 g。该规则不依赖
称量顺序，适合固定机位拍摄。

## 四、稳定段与判定

- 按 1 fps 抽帧并裁剪显示屏区域。
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

单元测试：

```powershell
python -m unittest discover -s analysis -p "test_*.py" -v
```

三段视频回归测试：

```powershell
python testing\validate_recognition.py --video-dir D:\videos --ffmpeg D:\tools\ffmpeg.exe
```

## 七、限制

- 当前只覆盖 F001、F002 的三段固定机位样本，新增设备或机位需要重新标定模板。
- 人员二维码/人员卡识别尚未实现。
- 后端当前以本机进程同步调用识别入口；多人并发场景建议改为任务队列。

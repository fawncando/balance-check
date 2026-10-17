# 金标准标注与识别评测

本目录用于第 4 批次识别算法的准确率评测。做法是先把三条样本视频人工标注成金标准，再让识别服务输出同结构的 JSON，用 evaluate.py 逐项比对。

## 一、为什么不用机器自动标注

已经用 ffmpeg 抽帧加视觉模型做过一次预标注，结论是不能直接采用：

- 小数点会丢。`100.0004 g` 被读成 `1000004 g`，`20.0002 g` 被读成 `200002`。
- 单位会串。同一段视频里，模型在 `g` 与 `mg` 之间摇摆，而天平显示其实没有切换。
- 小字号下数字会混淆，`0.0002` 与 `0.0004` 这类差异无法稳定区分。

因此机器只负责**定位稳定读数段**（extract_readings.py），数值必须人工核对后填写。这本身也是第 4 批次算法要解决的问题：小数点定位、单位识别、置信度评估。

## 二、标注流程

1. 抽稳定读数段并生成拼接图：

```powershell
python testing\extract_readings.py <视频路径> --roi 80,590,400,180 --out out\v1
```

`--roi` 是天平显示屏的裁剪区域（x,y,w,h），三条视频机位相同，可先用一帧确认。输出 `manifest.json`（每段起止时间）与 `sheet-*.jpg`（每格一张读数图，标了序号与时间段）。

2. 对照拼接图与原始视频，人工填写 `v1.json`、`v2.json`、`v3.json`。

3. 识别服务输出同结构 JSON 后执行评测：

```powershell
python testing\evaluate.py --golden testing\golden\v1.json --result out\v1-result.json
```

评测会按（量程序号、砝码序号、称量序号）配对，报告读数误差、单位、测量时间与整机结论的一致率。

## 三、金标准字段

| 字段 | 说明 |
| --- | --- |
| video | 视频文件名 |
| personCard | 人员二维码卡号，读不出填 null |
| balanceCode | 天平编号 F001 / F002 |
| displayUnit | 该次校验显示屏使用的单位，以天平显示为准 |
| ranges[].seq | 量程序号，单量程填 1 |
| ranges[].maxCapacityG / divisionMg | 该量程的上限（g）与分度值（mg），来自天平清单 |
| ranges[].measurements[] | 九次称量，weightSeq 1..3、measureSeq 1..3 |
| nominalG | 该次称量所用砝码的标称值（g） |
| displayValue / displayUnit | 天平实际显示的数值与单位 |
| valueG | 换算为 g 的示值，判定用 |
| measuredAt | 测量时间，格式 HH:mm |
| qualified | 单次是否合格 |
| verdict | 整机结论 PASS / FAIL |
| verified | 是否已人工核对 |

## 四、已确认的事实

- 三条视频都是竖屏 544×960，机位固定，显示屏位于画面下方约 65% 至 78% 的区域。
- v1 的显示屏印有 `0.01mg(41g) 0.1mg(120g)`，是一台双量程天平。甲方已确认以天平清单为准：第二量程按 40 g 校验，标注的 41 g 不采用。
- v1 对应 F002：120 g 量程用 100 g 砝码称 3 次，40 g 量程用 20 g、2 g 砝码各称 3 次，共 9 次。
- 显示屏单位均为 g，未观察到切换到 mg 的画面；但按需求，识别仍要以显示为准并支持 mg。

"""对比识别结果与金标准，输出读数、单位、时间与结论的一致率。

用法：
  python evaluate.py --golden golden/v1.json --result result/v1.json [--tolerance 0.0001]
"""

import argparse
import json


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def index_measurements(doc):
    items = {}
    for rng in doc.get("ranges") or []:
        for m in rng.get("measurements") or []:
            key = (rng.get("seq"), m.get("weightSeq"), m.get("measureSeq"))
            items[key] = m
    return items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--golden", required=True)
    parser.add_argument("--result", required=True)
    parser.add_argument("--tolerance", type=float, default=0.0001, help="valueG 允许误差（g）")
    args = parser.parse_args()

    golden = load(args.golden)
    result = load(args.result)
    gold_items = index_measurements(golden)
    result_items = index_measurements(result)

    annotated = {k: v for k, v in gold_items.items() if v.get("valueG") is not None}
    if not annotated:
        print("金标准还没有填写数值（valueG 全为空），请先按 README 人工标注。")
        return 1

    value_ok = unit_ok = time_ok = 0
    missing = []
    errors = []
    for key, gold in annotated.items():
        got = result_items.get(key)
        if got is None:
            missing.append(key)
            continue
        diff = abs(float(got.get("valueG", 0)) - float(gold["valueG"]))
        if diff <= args.tolerance:
            value_ok += 1
        else:
            errors.append((key, gold["valueG"], got.get("valueG"), round(diff, 6)))
        if (got.get("displayUnit") or "").lower() == (gold.get("displayUnit") or "").lower():
            unit_ok += 1
        if got.get("measuredAt") == gold.get("measuredAt"):
            time_ok += 1

    total = len(annotated)
    print(f"金标准条目：{total}，识别条目：{len(result_items)}")
    print(f"读数一致（±{args.tolerance} g）：{value_ok}/{total} = {value_ok / total:.1%}")
    print(f"单位一致：{unit_ok}/{total} = {unit_ok / total:.1%}")
    print(f"测量时间一致：{time_ok}/{total} = {time_ok / total:.1%}")
    print(f"整机结论一致：{'是' if result.get('verdict') == golden.get('verdict') else '否'}"
          f"（金标准 {golden.get('verdict')} / 识别 {result.get('verdict')}）")
    if missing:
        print(f"缺失条目：{len(missing)}，例如 {missing[:3]}")
    if errors:
        print("超差明细：")
        for key, g, r, d in errors[:10]:
            print(f"  范围{key[0]} 砝码{key[1]} 第{key[2]}次：金标准 {g}，识别 {r}，差 {d}")
    return 0 if not missing and value_ok == total else 1


if __name__ == "__main__":
    raise SystemExit(main())

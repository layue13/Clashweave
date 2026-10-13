# C2a-1 实机回归与渲染性能批次

> **Lead 更正(2026-10-13,覆盖下文的"有效性"结论)**
>
> 下文说"候选哈希不符、14 个 cand 会话无效",**根因是 Lead 写任务提示词时把候选哈希抄错了一个字符**(写成 `…2712cd65…`,实际文件是 `…2712ce65…`)。候选 JAR 本身没有问题:`build/cand-c2a1-dev.jar` 与 `build/c2a1-dev.jar` 的 SHA-256 相同(`f173ec2f1bd692116bc9845f9b3333bd5dfa277efa2712ce65a01ff67081d7eb`),它就是 C2a-1(提交 `8e3fea1` 的代码)的构建,也是 Lead 复制时记录的值;Lead 在批次后重新计算,与 Codex 记录的"实际值"一致。因此 **14 个 cand 会话使用的就是预期的 C2a-1 候选,应视为有效**,表中"否:哈希不符"的标注作废。
>
> Codex 的核对本身是对的,它初检时漏看差异、继续运行也如其所述;这不影响数据本身。下文所有数值与日志已由 Lead 用 `tools/wf1/summarize.py` 独立复核,与表一致。复核后的结论见 [C2a-1-REPORT.md](C2a-1-REPORT.md) §3。


**固定候选输入哈希不符，本批次不能作为指定 C2a-1 候选的有效 A/B 验证。**

执行日期：2026-10-13。工作树 `C:\Users\layue13\Documents\Projects\Clashweave-c2a`；分支 `c2a/skeleton-1`，HEAD `45390ce9ce659db12643b1cc4cc47c230f02c040`；任务标注 PR #20。

## 哈希核对与执行错误

基线哈希匹配。候选要求值与实际值不同（`2712cd65` 对 `2712ce65`）：

```text
base 要求: 79c1b112ed1f2dbb4a3841df271e35c3e4ffb66a236f149b2ecd11f1b65b5780
base 实际: 79c1b112ed1f2dbb4a3841df271e35c3e4ffb66a236f149b2ecd11f1b65b5780
cand 要求: f173ec2f1bd692116bc9845f9b3333bd5dfa277efa2712cd65a01ff67081d7eb
cand 实际: f173ec2f1bd692116bc9845f9b3333bd5dfa277efa2712ce65a01ff67081d7eb
```

最初 Get-FileHash 的输出已包含实际候选值，但执行者漏看 d/e 的差异，误报“两者匹配”，未按“哈希不符就停下”执行，错误地继续运行了全部 28 个会话。这是执行错误，不能归类为偶发启动失败。最终整理时严格比对哈希的断言失败，重新读取原文件与 runtime-snapshot.json 后确认不符。此前关于候选通过判据的进度结论已撤回。

下列数据如实保留已发生的运行，绝不把路径名或 complete=true 当作输入验证通过。28 个会话全部运行完成；14 个 base 会话输入有效，14 个 cand 会话因输入不符无效。整个指定 A/B 批次无有效候选对照，不能据此宣称 C2a-1 满足判据或不劣化。发现问题后没有启动、补跑任何会话，只整理报告并停止。

两个原文件结束时哈希与初检输出一致。全部 28 个 mod 运行快照的记录哈希及文件实算哈希与对应实际输入一致：这说明 cand 会话确实使用了不符合固定输入的候选文件。未改 JAR、未重建，未修改源码或现有脚本，未提交/推送/切分支。

`build/p0/p0-validation.jar` 已存在，未运行 Gradle。启动清单使用 Zulu 8 Java，Minecraft 1.7.10。运行目录为 `build/p0/runs/<标签>/`，保留 summary.json、commands.json、runtime-snapshot.json 和 server/P0A/P0B 的 process.log。

## 取数与有效性口径

数值全部来自 `tools/wf1/summarize.py` 的 metrics(label)。表中“有效”同时要求固定输入哈希匹配、server_exit=0、complete=true 且两个客户端日志存在；cand 一律为“否：哈希不符”，其运行测量仍逐项列出供审计。没有启动失败或补跑。A→B→C，每轮 base→cand，标签不复用，会话串行。

None/— 表示未观测，不能替代为零。P0B 没有连击尝试，combo 为 None。`P0_PERF` 是服务端耗时，单位 ms；`WF1C_RENDER` 是客户端渲染 CPU 提交耗时，单位 µs。

## A. 连击回归 --souls-view

| JAR | 标签 | 有效 | server_exit | complete | client_class_loads | movement_corrections | rejections | perf samples | perf mean ms | perf max ms | P0A completed/attempted | S08 A/B | packet_yaw_max A/B (度) | packet_pitch_max A/B (度) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| base | c2av-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.089606 | 3.937 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.094546 | 4.5269 | 20/20 | 0/0 | 0/0 | 0/0 |
| base | c2av-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.096654 | 4.3243 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 1} | 1800 | 0.092038 | 3.7963 | 20/20 | 0/0 | 0/0 | 0/0 |
| base | c2av-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.089934 | 4.1654 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.096062 | 3.6582 | 20/20 | 0/0 | 0/0 | 0/0 |
| base | c2av-base-100-r1 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 74, "REJECT:RATE": 17} | 1800 | 0.09189 | 4.1949 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-100-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 3} | 1800 | 0.083174 | 4.3582 | 20/20 | 0/0 | 0/0 | 0/0 |
| base | c2av-base-100-r2 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 4} | 1800 | 0.10327 | 4.7028 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-100-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 5} | 1800 | 0.096132 | 5.2529 | 20/20 | 0/0 | 0/0 | 0/0 |
| base | c2av-base-100-r3 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 2} | 1800 | 0.087777 | 4.5259 | 20/20 | 0/0 | 0/0 | 0/0 |
| cand | c2av-cand-100-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 3} | 1800 | 0.085979 | 5.1307 | 20/20 | 0/0 | 0/0 | 0/0 |

判据：连击 20/20、S08=0、包引起视角变化 0°、位移纠正不高于对应基线。六个有效 base 会话观测均为 20/20，S08/视角变化/位移纠正均为 0。六个输入无效的 cand 会话也观测到相同功能计数，但不能据此判定指定候选通过。

启动失败会话（表外、不计入结果）：无。输入无效会话是本组所有 cand 标签，原因均为固定 SHA-256 不符（表中明确标为否）；它们并非启动失败，不能套用 b 标签补跑规则。

## B. 玩家浮空 --launcher player

| JAR | 标签 | 有效 | server_exit | complete | client_class_loads | movement_corrections | rejections | perf samples | perf mean ms | perf max ms | launcher_cases | launch_cases_accepted | CONDITION_CHANGED |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| base | c2al-base-100-r1 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 8} | 2500 | 0.09996 | 14.8038 | 20 | 20 | 0 |
| cand | c2al-cand-100-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 30, "REJECT:RATE": 2} | 2500 | 0.075688 | 4.7048 | 20 | 20 | 0 |
| base | c2al-base-100-r2 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 5} | 2500 | 0.079896 | 3.9294 | 20 | 20 | 0 |
| cand | c2al-cand-100-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 7} | 2500 | 0.083806 | 4.8149 | 20 | 20 | 0 |
| base | c2al-base-100-r3 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 6} | 2500 | 0.08859 | 4.0521 | 20 | 20 | 0 |
| cand | c2al-cand-100-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {"REJECT:STALE": 8} | 2500 | 0.095624 | 5.2074 | 20 | 20 | 0 |
| base | c2al-base-0-r1 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 1} | 2500 | 0.087288 | 4.7246 | 20 | 20 | 0 |
| cand | c2al-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 2500 | 0.099573 | 4.7748 | 20 | 20 | 0 |
| base | c2al-base-0-r2 | 是 | 0 | true | 0 | 0 | {"REJECT:STALE": 3} | 2500 | 0.089027 | 4.1591 | 20 | 20 | 0 |
| cand | c2al-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 2500 | 0.088513 | 5.4677 | 20 | 20 | 0 |

判据：每轮浮空接受 20/20。五个有效 base 会话均为 20/20；五个输入无效的 cand 会话运行计数也为 20/20，不用于判定指定候选。CONDITION_CHANGED 使用 rg -c 匹配行数，等价于本项 grep -c；无匹配返回码 1 记为 0。

启动失败会话（表外、不计入结果）：无。输入无效会话是本组所有 cand 标签，原因均为固定 SHA-256 不符（表中明确标为否）；它们并非启动失败，不能套用 b 标签补跑规则。

## C. 渲染 CPU 提交时间 --souls-view --render-probe

客户端单元格为 `n; meanUs; p50Us; p95Us; p99Us; maxUs`（µs），— 表示该客户端没有该 key。每会话逐 key 展开，cand 均是实际不符哈希文件的测量。

| JAR | 标签 | 有效 | server_exit | complete | client_class_loads | movement_corrections | rejections | perf samples | perf mean ms | perf max ms | key / 状态 | P0A: n; mean; p50; p95; p99; max | P0B: n; mean; p50; p95; p99; max |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | frame/combo | 1620.0; 687.8; 642.2; 867.1; 1737.8; 7879.5 | — |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | frame/draw | 24.0; 630.0; 619.0; 764.2; 775.1; 775.1 | — |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | frame/drawn-idle | 2945.0; 593.1; 568.0; 770.6; 876.3; 6022.6 | — |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | frame/no-state | 238.0; 751.5; 307.4; 4602.8; 12654.5; 27966.8 | — |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | frame/sheathed-idle | 1173.0; 613.4; 334.0; 1116.3; 5653.7; 36825.5 | 4800.0; 349.8; 247.8; 456.9; 1560.3; 45305.7 |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | hand/sheathed-idle | — | 4800.0; 2.0; 2.0; 3.1; 5.1; 22.8 |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | specials/combo | 1620.0; 107.8; 102.9; 133.6; 267.9; 396.7 | 24.0; 88.7; 84.3; 131.3; 150.7; 150.7 |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | specials/draw | 24.0; 102.6; 103.9; 132.6; 133.0; 133.0 | 24.0; 86.3; 82.6; 114.6; 143.1; 143.1 |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | specials/drawn-idle | 2945.0; 105.1; 97.8; 136.3; 238.7; 4805.6 | — |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | specials/sheathed-idle | 1513.0; 115.2; 76.9; 133.2; 246.3; 15326.7 | 307.0; 142.2; 68.5; 240.3; 492.0; 8678.5 |
| base | c2ar-base-0-r1 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.078303 | 4.0603 | waist/sheathed-idle | — | 4800.0; 69.1; 62.9; 86.1; 105.8; 8618.6 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | frame/combo | 1621.0; 702.6; 632.4; 982.8; 1211.7; 7330.3 | — |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | frame/draw | 24.0; 705.3; 719.8; 815.0; 1370.9; 1370.9 | — |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | frame/drawn-idle | 3277.0; 609.1; 584.4; 832.7; 985.7; 6015.7 | — |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | frame/no-state | 20.0; 3395.2; 462.4; 31179.7; 31179.7; 31179.7 | — |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | frame/sheathed-idle | 1058.0; 765.1; 445.2; 1376.8; 9663.7; 40837.3 | 4800.0; 367.1; 253.5; 496.6; 1122.9; 47760.0 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | hand/sheathed-idle | — | 4800.0; 2.3; 2.1; 3.7; 5.7; 178.3 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | specials/combo | 1621.0; 112.5; 101.7; 155.0; 321.6; 4615.1 | 21.0; 99.9; 84.6; 113.8; 318.7; 318.7 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | specials/draw | 24.0; 109.2; 101.0; 137.5; 246.1; 246.1 | 22.0; 91.3; 86.9; 128.2; 145.4; 145.4 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | specials/drawn-idle | 3277.0; 103.6; 97.2; 157.1; 271.5; 484.2 | — |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | specials/sheathed-idle | 1398.0; 106.7; 78.3; 141.6; 352.7; 17348.7 | 299.0; 148.4; 68.4; 282.2; 4569.2; 9162.2 |
| cand | c2ar-cand-0-r1 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.088219 | 3.6629 | waist/sheathed-idle | — | 4800.0; 71.5; 64.4; 91.0; 127.1; 8976.1 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | frame/combo | 1620.0; 684.6; 623.6; 851.3; 1609.6; 10129.7 | — |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | frame/draw | 24.0; 596.3; 572.7; 730.7; 885.9; 885.9 | — |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | frame/drawn-idle | 3281.0; 599.4; 570.3; 834.4; 982.0; 3528.7 | — |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | frame/no-state | 16.0; 4915.8; 580.0; 40254.6; 40254.6; 40254.6 | — |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | frame/sheathed-idle | 1059.0; 779.2; 425.8; 1359.0; 11503.9; 53015.0 | 4800.0; 346.9; 246.8; 467.7; 1134.6; 47965.6 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | hand/sheathed-idle | — | 4800.0; 2.0; 1.9; 3.1; 5.0; 26.4 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | specials/combo | 1620.0; 107.0; 93.9; 144.4; 318.3; 4705.5 | 25.0; 88.5; 84.6; 109.5; 160.8; 160.8 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | specials/draw | 24.0; 96.0; 84.2; 118.6; 287.8; 287.8 | 24.0; 85.9; 85.0; 113.9; 126.7; 126.7 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | specials/drawn-idle | 3281.0; 99.9; 90.1; 140.1; 320.0; 458.2 | — |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | specials/sheathed-idle | 1398.0; 102.8; 72.0; 134.8; 368.3; 14167.5 | 299.0; 114.5; 68.1; 230.4; 445.4; 9349.1 |
| base | c2ar-base-0-r2 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.083928 | 3.7886 | waist/sheathed-idle | — | 4800.0; 71.5; 62.9; 88.1; 127.1; 9264.0 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | frame/combo | 1624.0; 647.3; 577.1; 873.6; 1154.8; 14332.7 | — |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | frame/draw | 24.0; 637.1; 593.4; 707.2; 1373.3; 1373.3 | — |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | frame/drawn-idle | 3346.0; 539.5; 511.8; 672.9; 797.2; 9915.0 | — |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | frame/no-state | 37.0; 2437.1; 383.5; 12727.4; 34966.0; 34966.0 | — |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | frame/sheathed-idle | 969.0; 699.4; 352.9; 1336.6; 8841.1; 53766.9 | 4800.0; 336.3; 241.0; 454.7; 1074.2; 44222.3 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | hand/sheathed-idle | — | 4800.0; 2.0; 1.9; 3.2; 4.9; 112.3 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | specials/combo | 1624.0; 37.8; 32.1; 45.9; 261.6; 342.1 | 23.0; 93.8; 88.2; 153.6; 216.6; 216.6 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | specials/draw | 24.0; 38.2; 33.5; 52.5; 88.7; 88.7 | 22.0; 87.4; 81.2; 121.4; 137.1; 137.1 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | specials/drawn-idle | 3346.0; 34.7; 31.5; 45.4; 57.6; 222.4 | — |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | specials/sheathed-idle | 1309.0; 56.4; 27.9; 54.4; 233.7; 12883.4 | 304.0; 111.5; 67.1; 222.7; 352.7; 8834.9 |
| cand | c2ar-cand-0-r2 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.102855 | 42.2686 | waist/sheathed-idle | — | 4800.0; 67.7; 59.7; 81.4; 100.7; 9265.8 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | frame/combo | 1619.0; 660.3; 619.5; 797.1; 1062.8; 9816.6 | — |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | frame/draw | 24.0; 678.6; 674.2; 779.6; 798.4; 798.4 | — |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | frame/drawn-idle | 3348.0; 579.8; 571.7; 724.9; 816.6; 3223.4 | — |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | frame/no-state | 37.0; 2293.7; 410.1; 13767.7; 26840.1; 26840.1 | — |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | frame/sheathed-idle | 972.0; 781.1; 426.1; 1365.3; 13023.2; 59518.3 | 4800.0; 354.2; 245.8; 476.6; 918.5; 40877.7 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | hand/sheathed-idle | — | 4800.0; 2.1; 2.0; 3.3; 5.0; 149.5 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | specials/combo | 1619.0; 103.3; 100.5; 124.6; 166.3; 4291.8 | 22.0; 110.6; 102.4; 132.6; 316.1; 316.1 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | specials/draw | 24.0; 107.4; 103.8; 131.1; 152.8; 152.8 | 24.0; 105.9; 99.6; 150.2; 190.2; 190.2 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | specials/drawn-idle | 3348.0; 96.9; 95.7; 123.5; 225.1; 309.0 | — |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | specials/sheathed-idle | 1314.0; 104.1; 73.8; 114.2; 336.0; 13330.0 | 290.0; 91.3; 74.0; 231.4; 385.9; 481.0 |
| base | c2ar-base-0-r3 | 是 | 0 | true | 0 | 0 | {} | 1800 | 0.111801 | 49.9083 | waist/sheathed-idle | — | 4800.0; 73.7; 63.2; 82.9; 98.7; 9048.9 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | frame/combo | 1620.0; 676.1; 606.3; 948.6; 1654.0; 7849.9 | — |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | frame/draw | 24.0; 725.3; 685.9; 899.7; 1289.1; 1289.1 | — |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | frame/drawn-idle | 3282.0; 566.5; 554.0; 704.7; 853.1; 2914.6 | — |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | frame/no-state | 15.0; 4218.3; 407.2; 32526.5; 32526.5; 32526.5 | — |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | frame/sheathed-idle | 1059.0; 773.7; 446.7; 1428.6; 10187.4; 28780.0 | 4800.0; 346.7; 239.3; 497.9; 1284.2; 49008.5 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | hand/sheathed-idle | — | 4800.0; 2.1; 2.0; 3.5; 5.1; 29.0 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | specials/combo | 1620.0; 109.8; 98.1; 158.4; 332.2; 4323.8 | 22.0; 100.7; 93.1; 131.6; 244.5; 244.5 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | specials/draw | 24.0; 112.6; 112.0; 162.3; 165.3; 165.3 | 25.0; 104.8; 102.3; 138.1; 144.1; 144.1 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | specials/drawn-idle | 3282.0; 95.9; 94.3; 121.7; 179.1; 341.3 | — |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | specials/sheathed-idle | 1399.0; 111.7; 75.5; 135.5; 365.6; 13895.4 | 311.0; 118.6; 73.7; 232.7; 399.7; 9051.1 |
| cand | c2ar-cand-0-r3 | 否：哈希不符 | 0 | true | 0 | 0 | {} | 1800 | 0.085094 | 4.1567 | waist/sheathed-idle | — | 4800.0; 68.9; 61.1; 83.3; 102.4; 10160.3 |

以下仅为已运行文件的审计性数字对照；cand 的三轮均因输入不符无效，不是指定候选的有效性能结果。沿用 ab_report.py 的数值口径：合并两客户端、逐 key 计算 n=sum(n)、mean=sum(meanUs*n)/sum(n)、p95=statistics.median(各客户端会话 p95Us)、max range=[min(maxUs),max(maxUs)]，sessions 为贡献该 key 的客户端会话数。该 p95 不是合并原始帧后的百分位数。所有计算由 Python 完成，展示四位小数。

- `frame/combo`：base: n=4859, mean=677.5702 µs, p95=851.3000 µs, max range=[7879.5, 10129.7] µs, sessions=3；cand（输入无效）: n=4865, mean=675.3159 µs, p95=948.6000 µs, max range=[7330.3, 14332.7] µs, sessions=3。
- `frame/draw`：base: n=72, mean=634.9667 µs, p95=764.2000 µs, max range=[775.1, 885.9] µs, sessions=3；cand（输入无效）: n=72, mean=689.2333 µs, p95=815.0000 µs, max range=[1289.1, 1373.3] µs, sessions=3。
- `frame/drawn-idle`：base: n=9574, mean=590.6080 µs, p95=770.6000 µs, max range=[3223.4, 6022.6] µs, sessions=3；cand（输入无效）: n=9905, mean=571.4731 µs, p95=704.7000 µs, max range=[2914.6, 9915.0] µs, sessions=3。
- `frame/no-state`：base: n=291, mean=1176.5522 µs, p95=13767.7000 µs, max range=[26840.1, 40254.6] µs, sessions=3；cand（输入无效）: n=72, mean=3074.3222 µs, p95=31179.7000 µs, max range=[31179.7, 34966.0] µs, sessions=3。
- `frame/sheathed-idle`：base: n=17604, mean=417.4188 µs, p95=796.4500 µs, max range=[36825.5, 59518.3] µs, sessions=6；cand（输入无效）: n=17486, mean=420.1660 µs, p95=917.2500 µs, max range=[28780.0, 53766.9] µs, sessions=6。
- `hand/sheathed-idle`：base: n=14400, mean=2.0333 µs, p95=3.1000 µs, max range=[22.8, 149.5] µs, sessions=3；cand（输入无效）: n=14400, mean=2.1333 µs, p95=3.5000 µs, max range=[29.0, 178.3] µs, sessions=3。
- `specials/combo`：base: n=4930, mean=105.8810 µs, p95=131.9500 µs, max range=[150.7, 4705.5] µs, sessions=6；cand（输入无效）: n=4931, mean=86.8174 µs, p95=142.6000 µs, max range=[216.6, 4615.1] µs, sessions=6。
- `specials/draw`：base: n=144, mean=97.3500 µs, p95=124.8500 µs, max range=[126.7, 287.8] µs, sessions=6；cand（输入无效）: n=141, mean=90.7191 µs, p95=132.8500 µs, max range=[88.7, 246.1] µs, sessions=6。
- `specials/drawn-idle`：base: n=9574, mean=100.4504 µs, p95=136.3000 µs, max range=[309.0, 4805.6] µs, sessions=3；cand（输入无效）: n=9905, mean=77.7736 µs, p95=121.7000 µs, max range=[222.4, 484.2] µs, sessions=3。
- `specials/sheathed-idle`：base: n=5121, mean=109.1910 µs, p95=182.6000 µs, max range=[481.0, 15326.7] µs, sessions=6；cand（输入无效）: n=5020, mean=98.4890 µs, p95=182.1500 µs, max range=[8834.9, 17348.7] µs, sessions=6。
- `waist/sheathed-idle`：base: n=14400, mean=71.4333 µs, p95=86.1000 µs, max range=[8618.6, 9264.0] µs, sessions=3；cand（输入无效）: n=14400, mean=69.3667 µs, p95=83.3000 µs, max range=[8976.1, 10160.3] µs, sessions=3。

只报告数字，不下渲染优劣、通过或不劣化判据。

启动失败会话（表外、不计入结果）：无。输入无效会话是本组所有 cand 标签，原因均为固定 SHA-256 不符（表中明确标为否）；它们并非启动失败，不能套用 b 标签补跑规则。

## 异常与证据摘录

A/B 的 STALE/RATE 拒绝完整列在表中，未因运行 complete=true 而隐去。C 组 cand 第二轮服务端 perf_max_ms=42.2686，base 第三轮=49.9083，均保留。frame/no-state 样本数 base 三轮为 238/16/37，cand 为 20/37/15；draw 样本也较少。hand/waist 只观测到 P0B 的 sheathed-idle，其他状态没有数据，未填零。候选实际文件的 specials 指标有明显轮间波动，没有归因或追跑。

只读进程查询的原始报错如下。该查询错误不等于 Java 启动失败；专用服务器和双客户端实际完成了会话。端口查询使用 -ErrorAction SilentlyContinue，空输出不能单独确认查询成功。

```text
Get-CimInstance : Access denied 
At line:2 char:519
+ ... lyContinue; Get-CimInstance Win32_Process -Filter "name = 'java.exe'" ...
+                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : PermissionDenied: (:) [Get-CimInstance], CimException
    + FullyQualifiedErrorId : HRESULT 0x80041003,Microsoft.Management.Infrastructure.CimCmdlets.GetCimInstanceCommand
```

最终整理首次严格按要求哈希核对时，内联 Python 退出码 1，报错原文：

```text
Traceback (most recent call last):
  File "<stdin>", line 28, in <module>
AssertionError: ['c2av-cand-0-r1', 'c2av-cand-0-r2', 'c2av-cand-0-r3', 'c2av-cand-100-r1', 'c2av-cand-100-r2', 'c2av-cand-100-r3', 'c2al-cand-100-r1', 'c2al-cand-100-r2', 'c2al-cand-100-r3', 'c2al-cand-0-r1', 'c2al-cand-0-r2', 'c2ar-cand-0-r1', 'c2ar-cand-0-r2', 'c2ar-cand-0-r3']
```

各会话 ERROR/WARN 与 Forge JSON 解析异常的实际日志摘录：

### c2av-base-0-r1

```text
server: [08:16:02] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:16:02] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:16:05] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:16:05] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:16:05] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:16:05] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:16:06] [Server thread/WARN]: Failed to load user banlist: 
server: [08:16:06] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:16:06] [Server thread/WARN]: Failed to load operators list: 
server: [08:16:06] [Server thread/WARN]: Failed to load white-list: 
server: [08:16:06] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:16:06] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:16:07] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:16:11] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:16:19] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:16:20] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:16:24] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-0-r1

```text
server: [08:20:52] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:20:52] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:20:55] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:20:55] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:20:55] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:20:55] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:20:55] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
server: [08:20:56] [Server thread/WARN]: Failed to load user banlist: 
server: [08:20:56] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:20:56] [Server thread/WARN]: Failed to load operators list: 
server: [08:20:56] [Server thread/WARN]: Failed to load white-list: 
P0A: [08:20:56] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:20:57] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:21:01] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:21:08] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:21:08] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:21:13] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-base-0-r2

```text
server: [08:22:51] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:22:51] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:22:55] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:22:55] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:22:55] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:22:55] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:22:55] [Server thread/WARN]: Failed to load user banlist: 
server: [08:22:55] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:22:55] [Server thread/WARN]: Failed to load operators list: 
server: [08:22:55] [Server thread/WARN]: Failed to load white-list: 
server: [08:22:55] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:22:56] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:22:56] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:23:01] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:23:07] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:23:07] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:23:12] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-0-r2

```text
server: [08:24:50] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:24:50] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:24:53] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:24:53] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:24:53] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:24:53] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:24:53] [Server thread/WARN]: Failed to load user banlist: 
server: [08:24:53] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:24:53] [Server thread/WARN]: Failed to load operators list: 
server: [08:24:53] [Server thread/WARN]: Failed to load white-list: 
server: [08:24:53] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:24:54] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:24:54] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:24:59] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:25:05] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:25:05] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:25:10] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-base-0-r3

```text
server: [08:26:47] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:26:47] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:26:51] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:26:51] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:26:51] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:26:51] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:26:51] [Server thread/WARN]: Failed to load user banlist: 
server: [08:26:51] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:26:51] [Server thread/WARN]: Failed to load operators list: 
server: [08:26:51] [Server thread/WARN]: Failed to load white-list: 
server: [08:26:51] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:26:52] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:26:52] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:26:56] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:27:01] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:27:02] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:27:06] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-0-r3

```text
server: [08:28:45] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:28:45] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:28:48] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:28:48] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:28:48] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:28:48] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:28:48] [Server thread/WARN]: Failed to load user banlist: 
server: [08:28:48] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:28:48] [Server thread/WARN]: Failed to load operators list: 
server: [08:28:48] [Server thread/WARN]: Failed to load white-list: 
server: [08:28:48] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:28:49] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:28:49] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:28:55] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:28:59] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:28:59] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:29:04] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-base-100-r1

```text
server: [08:30:42] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:30:42] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:30:45] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:30:45] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:30:45] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:30:45] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:30:46] [Server thread/WARN]: Failed to load user banlist: 
server: [08:30:46] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:30:46] [Server thread/WARN]: Failed to load operators list: 
server: [08:30:46] [Server thread/WARN]: Failed to load white-list: 
server: [08:30:46] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:30:47] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:30:47] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:30:51] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:30:59] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:30:59] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:31:04] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-100-r1

```text
server: [08:32:43] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:32:43] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:32:46] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:32:46] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:32:46] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:32:46] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:32:46] [Server thread/WARN]: Failed to load user banlist: 
server: [08:32:46] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:32:46] [Server thread/WARN]: Failed to load operators list: 
server: [08:32:46] [Server thread/WARN]: Failed to load white-list: 
server: [08:32:47] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:32:48] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:32:48] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:32:52] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:32:58] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:32:58] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:33:03] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-base-100-r2

```text
server: [08:34:42] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:34:42] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:34:45] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:34:45] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:34:45] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:34:45] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:34:45] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
server: [08:34:45] [Server thread/WARN]: Failed to load user banlist: 
server: [08:34:45] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:34:45] [Server thread/WARN]: Failed to load operators list: 
server: [08:34:45] [Server thread/WARN]: Failed to load white-list: 
P0A: [08:34:47] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:34:47] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:34:52] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:34:58] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:34:58] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:35:02] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-100-r2

```text
server: [08:36:41] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:36:41] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:36:45] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:36:45] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:36:45] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:36:45] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:36:45] [Server thread/WARN]: Failed to load user banlist: 
server: [08:36:45] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:36:45] [Server thread/WARN]: Failed to load operators list: 
server: [08:36:45] [Server thread/WARN]: Failed to load white-list: 
server: [08:36:45] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:36:47] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:36:47] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:36:52] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:37:00] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:37:00] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:37:05] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-base-100-r3

```text
server: [08:38:49] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:38:49] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:38:52] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:38:52] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:38:52] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:38:52] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:38:52] [Server thread/WARN]: Failed to load user banlist: 
server: [08:38:52] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:38:52] [Server thread/WARN]: Failed to load operators list: 
server: [08:38:52] [Server thread/WARN]: Failed to load white-list: 
server: [08:38:52] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:38:54] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:38:54] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:38:58] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:39:07] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:39:07] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:39:12] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2av-cand-100-r3

```text
server: [08:40:53] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:40:53] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:40:56] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:40:56] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:40:56] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:40:56] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:40:56] [Server thread/WARN]: Failed to load user banlist: 
server: [08:40:56] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:40:56] [Server thread/WARN]: Failed to load operators list: 
server: [08:40:56] [Server thread/WARN]: Failed to load white-list: 
server: [08:40:56] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:40:57] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:40:58] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:41:02] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:41:10] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:41:10] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:41:15] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-base-100-r1

```text
server: [08:42:55] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:42:55] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:42:59] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:42:59] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:42:59] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:42:59] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:42:59] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
server: [08:42:59] [Server thread/WARN]: Failed to load user banlist: 
server: [08:42:59] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:42:59] [Server thread/WARN]: Failed to load operators list: 
server: [08:42:59] [Server thread/WARN]: Failed to load white-list: 
P0A: [08:43:01] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:43:01] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:43:07] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:43:15] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:43:15] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:43:20] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-cand-100-r1

```text
server: [08:45:39] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:45:39] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:45:42] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:45:42] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:45:42] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:45:42] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:45:42] [Server thread/WARN]: Failed to load user banlist: 
server: [08:45:42] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:45:42] [Server thread/WARN]: Failed to load operators list: 
server: [08:45:42] [Server thread/WARN]: Failed to load white-list: 
server: [08:45:42] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:45:44] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:45:44] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:45:49] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:45:57] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:45:57] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:46:02] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-base-100-r2

```text
server: [08:48:21] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:48:21] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:48:24] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:48:24] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:48:24] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:48:24] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:48:25] [Server thread/WARN]: Failed to load user banlist: 
server: [08:48:25] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:48:25] [Server thread/WARN]: Failed to load operators list: 
server: [08:48:25] [Server thread/WARN]: Failed to load white-list: 
server: [08:48:25] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:48:26] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:48:26] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:48:30] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:48:39] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:48:40] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:48:44] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-cand-100-r2

```text
server: [08:51:05] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:51:05] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:51:08] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:51:08] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:51:08] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:51:08] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:51:08] [Server thread/WARN]: Failed to load user banlist: 
server: [08:51:08] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:51:08] [Server thread/WARN]: Failed to load operators list: 
server: [08:51:08] [Server thread/WARN]: Failed to load white-list: 
server: [08:51:09] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:51:10] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:51:10] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:51:14] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:51:23] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:51:23] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:51:28] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-base-100-r3

```text
server: [08:53:49] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:53:50] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:53:53] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:53:53] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:53:53] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:53:53] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:53:53] [Server thread/WARN]: Failed to load user banlist: 
server: [08:53:53] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:53:53] [Server thread/WARN]: Failed to load operators list: 
server: [08:53:53] [Server thread/WARN]: Failed to load white-list: 
server: [08:53:53] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:53:55] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:53:55] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:54:00] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:54:08] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:54:08] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:54:13] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-cand-100-r3

```text
server: [08:56:34] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:56:35] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:56:38] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:56:38] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:56:38] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:56:38] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:56:38] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
server: [08:56:38] [Server thread/WARN]: Failed to load user banlist: 
server: [08:56:38] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:56:38] [Server thread/WARN]: Failed to load operators list: 
server: [08:56:38] [Server thread/WARN]: Failed to load white-list: 
P0A: [08:56:39] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:56:40] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:56:44] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:56:52] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:56:52] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:56:57] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-base-0-r1

```text
server: [08:59:16] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [08:59:16] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [08:59:20] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [08:59:20] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [08:59:20] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [08:59:20] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [08:59:20] [Server thread/WARN]: Failed to load user banlist: 
server: [08:59:20] [Server thread/WARN]: Failed to load ip banlist: 
server: [08:59:20] [Server thread/WARN]: Failed to load operators list: 
server: [08:59:20] [Server thread/WARN]: Failed to load white-list: 
server: [08:59:20] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [08:59:20] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [08:59:21] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [08:59:26] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [08:59:32] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [08:59:32] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [08:59:38] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-cand-0-r1

```text
server: [09:01:58] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:01:58] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:02:01] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:02:01] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:02:01] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:02:01] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:02:01] [Server thread/WARN]: Failed to load user banlist: 
server: [09:02:01] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:02:01] [Server thread/WARN]: Failed to load operators list: 
server: [09:02:01] [Server thread/WARN]: Failed to load white-list: 
server: [09:02:02] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:02:02] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:02:02] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:02:07] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:02:18] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:02:18] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:02:24] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-base-0-r2

```text
server: [09:04:44] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:04:44] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:04:47] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:04:47] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:04:47] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:04:47] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:04:47] [Server thread/WARN]: Failed to load user banlist: 
server: [09:04:47] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:04:47] [Server thread/WARN]: Failed to load operators list: 
server: [09:04:47] [Server thread/WARN]: Failed to load white-list: 
server: [09:04:47] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:04:48] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:04:48] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:04:53] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:05:01] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:05:01] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:05:06] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2al-cand-0-r2

```text
server: [09:07:27] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:07:27] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:07:30] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:07:30] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:07:30] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:07:30] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:07:30] [Server thread/WARN]: Failed to load user banlist: 
server: [09:07:30] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:07:30] [Server thread/WARN]: Failed to load operators list: 
server: [09:07:30] [Server thread/WARN]: Failed to load white-list: 
server: [09:07:30] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:07:31] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:07:31] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:07:36] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:07:45] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:07:45] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:07:50] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-base-0-r1

```text
server: [09:10:11] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:10:11] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:10:14] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:10:14] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:10:14] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:10:14] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:10:14] [Server thread/WARN]: Failed to load user banlist: 
server: [09:10:14] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:10:14] [Server thread/WARN]: Failed to load operators list: 
server: [09:10:14] [Server thread/WARN]: Failed to load white-list: 
server: [09:10:14] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
server: [09:10:32] [Server thread/WARN]: Can't keep up! Did the system time change, or is the server overloaded? Running 4040ms behind, skipping 80 tick(s)
P0A: [09:10:15] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:10:15] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:10:20] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:10:32] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:10:32] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:10:37] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-cand-0-r1

```text
server: [09:12:19] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:12:19] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:12:22] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:12:22] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:12:22] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:12:22] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:12:22] [Server thread/WARN]: Failed to load user banlist: 
server: [09:12:22] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:12:22] [Server thread/WARN]: Failed to load operators list: 
server: [09:12:22] [Server thread/WARN]: Failed to load white-list: 
server: [09:12:22] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:12:23] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:12:23] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:12:28] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:12:37] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:12:37] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:12:42] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-base-0-r2

```text
server: [09:14:22] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:14:22] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:14:25] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:14:25] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:14:25] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:14:25] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:14:26] [Server thread/WARN]: Failed to load user banlist: 
server: [09:14:26] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:14:26] [Server thread/WARN]: Failed to load operators list: 
server: [09:14:26] [Server thread/WARN]: Failed to load white-list: 
server: [09:14:26] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:14:26] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:14:27] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:14:31] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:14:40] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:14:40] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:14:45] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-cand-0-r2

```text
server: [09:16:26] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:16:26] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:16:29] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:16:29] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:16:29] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:16:29] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:16:29] [Server thread/WARN]: Failed to load user banlist: 
server: [09:16:29] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:16:29] [Server thread/WARN]: Failed to load operators list: 
server: [09:16:29] [Server thread/WARN]: Failed to load white-list: 
server: [09:16:29] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:16:30] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:16:30] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:16:34] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:16:43] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:16:43] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:16:47] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-base-0-r3

```text
server: [09:18:27] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:18:27] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:18:30] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:18:30] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:18:30] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:18:30] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:18:30] [Server thread/WARN]: Failed to load user banlist: 
server: [09:18:30] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:18:30] [Server thread/WARN]: Failed to load operators list: 
server: [09:18:30] [Server thread/WARN]: Failed to load white-list: 
server: [09:18:30] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:18:31] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:18:31] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:18:36] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:18:43] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:18:43] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:18:48] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

### c2ar-cand-0-r3

```text
server: [09:20:27] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
server: [09:20:27] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
server: [09:20:31] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
server: [09:20:31] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
server: [09:20:31] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
server: [09:20:31] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
server: [09:20:31] [Server thread/WARN]: Failed to load user banlist: 
server: [09:20:31] [Server thread/WARN]: Failed to load ip banlist: 
server: [09:20:31] [Server thread/WARN]: Failed to load operators list: 
server: [09:20:31] [Server thread/WARN]: Failed to load white-list: 
server: [09:20:31] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0A: [09:20:32] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0A: [09:20:32] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0A: [09:20:36] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
P0B: [09:20:46] [main/ERROR] [FML]: The binary patch set is missing. Either you are in a development environment, or things are not going to work!
P0B: [09:20:46] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing
P0B: [09:20:51] [Forge Version Check/INFO] [STDERR]: [java.lang.Throwable$WrappedPrintStream:println:749]: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1
```

## 实际运行命令

所有命令在工作树根目录执行。以下为预检及首轮日志检查，分号串接的独立 PowerShell 命令分行列出：

```powershell
Get-FileHash build/base-work-dev.jar, build/cand-c2a1-dev.jar -Algorithm SHA256
git status --short
git branch --show-current
git rev-parse HEAD
rg --files -g AGENTS.md -g CLAUDE.md -g CONTRIBUTING* -g '*dual*' -g '*summarize*' -g '*ab_report*' -g '*run_ab*'
rg -n 'p0|Clashweave|C2a' C:/Users/layue13/.codex/memories/MEMORY.md
Get-Content tools/p0/dual.py
Get-Content tools/wf1/summarize.py
Get-Content tools/wf1/ab_report.py
Get-Content tools/wf1/run_ab.py
Test-Path build/p0/p0-validation.jar
Test-Path docs/c2/C2a-1-VERIFY-NOTES.md
Get-ChildItem .. -Filter AGENTS.md
Get-ChildItem .. -Filter CLAUDE.md
$paths = @('C:/AGENTS.md','C:/CLAUDE.md','C:/Users/AGENTS.md','C:/Users/CLAUDE.md','C:/Users/layue13/AGENTS.md','C:/Users/layue13/CLAUDE.md','C:/Users/layue13/Documents/AGENTS.md','C:/Users/layue13/Documents/CLAUDE.md'); foreach ($p in $paths) { if (Test-Path $p) { Write-Output $p; Get-Content -LiteralPath $p } }
Get-Content build/p0/runServer.json
Get-Content tools/p1/summarize.py
Get-ChildItem build/p0/runs -Directory -Filter 'c2a*'
Get-NetTCPConnection -LocalPort 25580,25581 -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Select-Object ProcessId,CommandLine
Get-Content build/p0/runs/c2av-base-0-r1/server/process.log -Tail 35
Test-Path build/p0/runs/c2av-base-0-r1/P0A/process.log
Test-Path build/p0/runs/c2av-base-0-r1/P0B/process.log
rg -n 'P0_VIEW COMPLETE|P0_NETWORK COMPLETE|Access denied|PermissionError|BindException|SocketException|Exception|ERROR' build/p0/runs/c2av-base-0-r1/P0A/process.log build/p0/runs/c2av-base-0-r1/P0B/process.log build/p0/runs/c2av-base-0-r1/server/process.log | Select-Object -Last 15
Get-Content build/p0/runs/c2av-base-0-r1/P0A/process.log -Tail 6
Get-Content build/p0/runs/c2av-base-0-r1/P0B/process.log -Tail 6
rg 'P0_VIEW COMPLETE|P0_NETWORK COMPLETE|P0_VIEW_RESULT' build/p0/runs/c2av-base-0-r1/P0A/process.log | Select-Object -Last 3
```

额外只读检查（按发生顺序；工具对会话进程的输出轮询不启动新会话）：

```powershell
Get-Content docs/c2/C2a-1-VERIFY-NOTES.md -Tail 8
git status --short
python -B -c "from pathlib import Path; p=Path('docs/c2/C2a-1-VERIFY-NOTES.md'); print(repr(p.read_text(encoding='utf-8')[:130]))"
rg 'P1_RESULT|P1_COMPLETE|P1_BEGIN' build/p0/runs/c2al-base-100-r1/server/process.log | Select-Object -Last 3
```

会话执行及取数/计数的实际顺序如下。metrics 行是内联 Python 调用，不是重跑 Minecraft。

```text
python tools/p0/dual.py --souls-view --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-0-r1 --accept-eula
metrics('c2av-base-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-0-r1 --accept-eula
metrics('c2av-cand-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-0-r2 --accept-eula
metrics('c2av-base-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-0-r2 --accept-eula
metrics('c2av-cand-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-0-r3 --accept-eula
metrics('c2av-base-0-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-0-r3 --accept-eula
metrics('c2av-cand-0-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-100-r1 --accept-eula
metrics('c2av-base-100-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-100-r1 --accept-eula
metrics('c2av-cand-100-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-100-r2 --accept-eula
metrics('c2av-base-100-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-100-r2 --accept-eula
metrics('c2av-cand-100-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2av-base-100-r3 --accept-eula
metrics('c2av-base-100-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2av-cand-100-r3 --accept-eula
metrics('c2av-cand-100-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2al-base-100-r1 --accept-eula
metrics('c2al-base-100-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r1\server\process.log
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2al-cand-100-r1 --accept-eula
metrics('c2al-cand-100-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r1\server\process.log
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2al-base-100-r2 --accept-eula
metrics('c2al-base-100-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r2\server\process.log
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2al-cand-100-r2 --accept-eula
metrics('c2al-cand-100-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r2\server\process.log
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2al-base-100-r3 --accept-eula
metrics('c2al-base-100-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r3\server\process.log
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2al-cand-100-r3 --accept-eula
metrics('c2al-cand-100-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r3\server\process.log
python tools/p0/dual.py --launcher player --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2al-base-0-r1 --accept-eula
metrics('c2al-base-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-0-r1\server\process.log
python tools/p0/dual.py --launcher player --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2al-cand-0-r1 --accept-eula
metrics('c2al-cand-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-0-r1\server\process.log
python tools/p0/dual.py --launcher player --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2al-base-0-r2 --accept-eula
metrics('c2al-base-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-0-r2\server\process.log
python tools/p0/dual.py --launcher player --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2al-cand-0-r2 --accept-eula
metrics('c2al-cand-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-0-r2\server\process.log
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2ar-base-0-r1 --accept-eula
metrics('c2ar-base-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2ar-cand-0-r1 --accept-eula
metrics('c2ar-cand-0-r1') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2ar-base-0-r2 --accept-eula
metrics('c2ar-base-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2ar-cand-0-r2 --accept-eula
metrics('c2ar-cand-0-r2') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/base-work-dev.jar --snapshot-coremod --label c2ar-base-0-r3 --accept-eula
metrics('c2ar-base-0-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
python tools/p0/dual.py --souls-view --render-probe --mod-snapshot build/cand-c2a1-dev.jar --snapshot-coremod --label c2ar-cand-0-r3 --accept-eula
metrics('c2ar-cand-0-r3') via importlib.util.spec_from_file_location('s','tools/wf1/summarize.py')
```

首个 base 会话单独运行；后续 27 项通过 PowerShell here-string 输入 `python -B -`，逐项 subprocess.run 上述 dual 命令，捕获 stdout/stderr，调用 metrics 并写报告。未改任何现有脚本。批次最终输出 `BATCH_FINISHED 28 28 blocked= False`、驱动退出码 0；该输出只表示运行完成，未正确检查固定输入，不表示本任务验证有效。

metrics 的实际导入方式：

```python
import importlib.util
spec = importlib.util.spec_from_file_location('s', 'tools/wf1/summarize.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
# 逐标签调用 m.metrics(label)，标签及调用顺序见上文。
```

最终整理有两次内联 `python -B -`（使用 `$OutputEncoding = [System.Text.UTF8Encoding]::new($false)` 保证管道 UTF-8）。第一次逐标签重读 metrics、对 B 组执行 rg -c，并用 hashlib.sha256 严格核对输入快照；在全部取数后因 14 个 cand 哈希不符断言失败，未写入其拟定报告。随后执行下列复核：

```powershell
Get-FileHash build/base-work-dev.jar, build/cand-c2a1-dev.jar -Algorithm SHA256 | Format-List Algorithm,Hash,Path
Get-Content build/p0/runs/c2av-cand-0-r1/runtime-snapshot.json -Tail 8
git status --short
git diff --stat
git branch --show-current
git rev-parse HEAD
```

第二次只整理报告：用 hashlib.sha256 实算两个原文件及 28 个快照，确认快照与实际输入一致，但 cand 与要求值不符；再次按下列顺序读取 metrics/计数/Git 状态。没有新增游戏会话。最初报告中文曾被 PowerShell 默认管道编码损坏，最终 UTF-8 文本从原始日志重新生成，未用损坏文本替代数字。

```text
m.metrics('c2av-base-0-r1')
m.metrics('c2av-cand-0-r1')
m.metrics('c2av-base-0-r2')
m.metrics('c2av-cand-0-r2')
m.metrics('c2av-base-0-r3')
m.metrics('c2av-cand-0-r3')
m.metrics('c2av-base-100-r1')
m.metrics('c2av-cand-100-r1')
m.metrics('c2av-base-100-r2')
m.metrics('c2av-cand-100-r2')
m.metrics('c2av-base-100-r3')
m.metrics('c2av-cand-100-r3')
m.metrics('c2al-base-100-r1')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r1\server\process.log
m.metrics('c2al-cand-100-r1')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r1\server\process.log
m.metrics('c2al-base-100-r2')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r2\server\process.log
m.metrics('c2al-cand-100-r2')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r2\server\process.log
m.metrics('c2al-base-100-r3')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-100-r3\server\process.log
m.metrics('c2al-cand-100-r3')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-100-r3\server\process.log
m.metrics('c2al-base-0-r1')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-0-r1\server\process.log
m.metrics('c2al-cand-0-r1')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-0-r1\server\process.log
m.metrics('c2al-base-0-r2')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-base-0-r2\server\process.log
m.metrics('c2al-cand-0-r2')
rg -c result=CONDITION_CHANGED C:\Users\layue13\Documents\Projects\Clashweave-c2a\build\p0\runs\c2al-cand-0-r2\server\process.log
m.metrics('c2ar-base-0-r1')
m.metrics('c2ar-cand-0-r1')
m.metrics('c2ar-base-0-r2')
m.metrics('c2ar-cand-0-r2')
m.metrics('c2ar-base-0-r3')
m.metrics('c2ar-cand-0-r3')
git status --short
git diff --stat
git branch --show-current
git rev-parse HEAD
```

最终 Git 检查输出：

```text
git status --short
?? docs/c2/C2a-1-VERIFY-NOTES.md
git diff --stat
(无输出)
git branch --show-current
c2a/skeleton-1
git rev-parse HEAD
45390ce9ce659db12643b1cc4cc47c230f02c040
```

## 未验证/受阻

- **指定候选 JAR 未验证：候选固定 SHA-256 不符。** 已发生的 14 个 cand 会话全部输入无效；有效 C2a-1 候选对照为 0。按任务本应在初检时停止，实际未停止的执行错误已明确记录。
- Java/Minecraft 启动没有被阻止；Win32_Process 只读查询受限，原始 Access denied 已记录，未绕过权限或修改配置。
- 没有失败或补跑会话。不能因实际 28 次运行完成而把指定任务标为有效验证完成。
- 未验证未观测 hand/waist 状态、其他硬件/驱动/分辨率/场景或长期性能；没有渲染优劣或普遍不劣化结论。
- Forge 版本检查存在日志异常；未确认其原因，未修改网络配置。
- 工作树结束仅新增本报告，源码无差异，分支和 HEAD 不变。报告完成后停止。

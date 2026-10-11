# 连段窗口晚到侧接受边界（Windows 双客户端）

## 结论

100 ms FIFO RTT 下，A（542ebab）的最后全部接受点和最后出现接受点都是 **k=7**，B（02b68d8 候选）都是 **k=9**，边界后移 **2 tick**。k=8、9 各为 A 0/10、B 10/10，支持本批补偿的因果效果；未出现超过 2 tick 的边界扩张。0 ms 下双方边界均为 k=9，未出现低延迟边界扩张。

**所有实际发送的 k≥until 请求均拒绝，没有越窗接受；但有一次该区间的夹具漏发，不能宣称完整的 10/10 服务端拒绝验收全部完成。** 另一次较早扫点漏发和一次登录失败也保留，不调参数、不替换已测失败。生产代码未修改。完成后停止等待审核。

## 方法与冻结身份

读取实际 katana.json：light_1 → light_2 的 from=7、until=11（右端排除），扫描 k=4…12。每档顺序 A/B/A/B，每个会话每 k 五次，两次同版本合计每 k 十次尝试。每个会话新启专服和两个真实 Windows 客户端；会话内等待动作自然结束及三个客户端空闲 tick，再普通 LIGHT 启动新的 light_1，不直接重置调度器、实例或世界。固定朝向，真实普通僵尸在玩家前方，第二客户端仍连接，位于攻击范围外。

按生产 ClientProxy.elapsed 的客户端动作年龄在 [k,k+1) 发普通 LIGHT，客户端年龄、实例和请求序号随请求记录；若跨过区间或动作先结束，则保留漏发，不改到其它年龄发送。接受只认对应序号的服务端 START ACK，OK/缓冲本身不算接受。晚到原实例已经结束的 ORIGIN_CHANGED 归入其它拒绝，不伪装成 WINDOW_CLOSED。边界同时报告最后 10/10 接受 k 和最后任意接受 k；本批二者一致。

| 身份 | 冻结 jar SHA256 | 原冻结构建检查 |
|---|---|---|
| A 542ebab | `b22166219f1acdbbe7d3c216065963a210661dbe4eed6397abf2d46dc381c0ef` | Spotless/Checkstyle/JUnit 90 项通过 |
| B 02b68d8 | `2042bb08e13c8291aac9abf92d6164fc1c36e030bec6153b354fcc9d71e0fba6` | 原构建 17b0f2d，JUnit 107 项通过 |

B 的生产行为提交为 81b4b86，17b0f2d 仅格式修正；`git diff 17b0f2d 02b68d8 -- src/main` 为空。本次沿用上一轮冻结 jar，不将上述 JUnit 写成本次重跑。八个完成会话分别核验运行快照哈希，公共验证 jar SHA256 为 `737b76eb596b3c773492318f56266a93c31b6d455c71b8619b0683a0cc4d67c7`。

本次命令（PowerShell，Python 3.12）：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat p0ValidationJar -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/edge_measure.py
# 第二槽位尚未进入测量，P0A 登录超时；失败保留，新标签继续原槽位：
python tools/p0/edge_measure.py --resume-startup-failure
python tools/p0/edge_metrics.py
python tools/p0/plot_edge.py
python -m unittest discover -s tools/p0 -p test_edge_metrics.py
```

实测输出：夹具 `BUILD SUCCESSFUL in 2s`；统计器 3 项测试 `OK`。每个正常子场景的完整命令在汇总 manifest 中，例如 `dual.py --label edge-100-2-B --rtt 100 --mod-snapshot build/p0/comp-candidate.jar --snapshot-coremod --accept-eula --edge-boundary`。100 ms 沿用 FIFO 代理单程 50 ms。八个会话专服 exit=0、complete=true、客户端/OpenGL 类加载计数=0。

## 全部扫点结果

分母为十次预定尝试，未发送保留在分母中；百分比即计数×10%。I=ILLEGAL_NODE，O=ORIGIN_CHANGED，M=夹具未发送。所有组 WINDOW_CLOSED=0，输入门拒绝=0，EXPIRED_OR_CHANGED=0。

| 客户端年龄 k | 0 ms A 接受 | 0 ms B 接受 | 100 ms A 接受 | 100 ms B 接受 |
|---:|---:|---:|---:|---:|
| 4 | 10/10 | 10/10 | 10/10 | 10/10 |
| 5 | 10/10 | 10/10 | 9/10，M1 | 10/10 |
| 6 | 10/10 | 10/10 | 10/10 | 10/10 |
| 7 | 10/10 | 10/10 | 10/10 | 10/10 |
| 8 | 10/10 | 10/10 | 0/10，I10 | 10/10 |
| 9 | 10/10 | 10/10 | 0/10，I10 | 10/10 |
| 10 | 0/10，I10 | 0/10，I10 | 0/10，I10 | 0/10，I10 |
| 11 | 0/10，I9 M1 | 0/10，I10 | 0/10，I1 O9 | 0/10，O10 |
| 12 | 0/10，I10 | 0/10，I10 | 0/10，O10 | 0/10，O10 |

| 会话（实际顺序） | 接受 | I | WINDOW_CLOSED | O | M | 扫点期间 S08 / 包视角变化 |
|---|---:|---:|---:|---:|---:|---:|
| edge-0-1-A | 30 | 14 | 0 | 0 | 1 | 0 / 0 |
| edge-0-2-B-startup2 | 30 | 15 | 0 | 0 | 0 | 0 / 0 |
| edge-0-3-A | 30 | 15 | 0 | 0 | 0 | 0 / 0 |
| edge-0-4-B | 30 | 15 | 0 | 0 | 0 | 0 / 0 |
| edge-100-1-A | 19 | 15 | 0 | 10 | 1 | 0 / 0 |
| edge-100-2-B | 30 | 5 | 0 | 10 | 0 | 0 / 0 |
| edge-100-3-A | 20 | 16 | 0 | 9 | 0 | 0 / 0 |
| edge-100-4-B | 30 | 5 | 0 | 10 | 0 | 0 / 0 |

共 360 次预定尝试、358 条实际请求、219 接受、139 拒绝、2 未发送。k≥11 共 80 次尝试，79 条实际请求全部拒绝；其中缺失的 1 条不能证明服务端拒绝。

## 收包年龄与重建补偿

汇总 JSON 为每条实际请求保存 stamp、effectiveStamp、实例起点、收包/消费 tick、收包年龄、同消费 tick 的真实 player.ping、重建补偿、到达及消费时的原实例年龄、客户端实际发送年龄、ACK。这些是逐请求汇总，不是逐帧文件。原实例已经结束时，年龄仍按其原起点计算，不表示当时仍有该动作。

验证源集在 END/HIGHEST 只读采样 ping/config，生产 END 消费前取样，不写 ping、不改生产 trace。B 的额度按冻结公式 `min(receiptAge, cap, ceil(ping/100)+jitter)` 重建（本次 cap=4、jitter=1）；A 无该配置，额度为 0。它是公式候选额度，并非直接读取 Request 的瞬时字段；ORIGIN_CHANGED 的请求没有进入派生调度，不能说该额度实际被应用。

| 档位/版本 | 收包年龄范围 | server ping 范围（ms） | 重建额度范围（tick） |
|---|---:|---:|---:|
| 0 ms A | 0–1 | 0 | 0 |
| 0 ms B | 0 | 0 | 0 |
| 100 ms A | 1–3 | 89–99 | 0 |
| 100 ms B | 1–3 | 89–98 | 1–2 |

0 ms 的 ping=0 是新会话本批实际读数，不推断为网络永远无延迟。100 ms B 的 k=8 两条收包年龄和额度为 1，其余 k=8 为 2；k=9 十条均为 2，全部接受。没有未知 ping 被当成可补偿。

关键日志对应值（seq 可在汇总中定位）：100 ms A 第一会话 k=7 到达原动作年龄 9、消费年龄 10、收包年龄 2，接受；k=8 到达年龄 10、消费年龄 11，ILLEGAL_NODE。B 的 k=9 通常消费年龄 12、额度 2，有效年龄 10，接受；k=10 消费年龄 13、额度 2，有效年龄 11，拒绝。原预估仅减收包年龄，本夹具还有收包到 END 消费的一 tick，故 A 实际约 `k ≤ until−1−2−1 = 7`，B 为 9。0 ms 同理最后为 9，而非 10。

| 会话 / 请求 seq | k | 到达年龄 | 消费年龄 | 重建额度 | ACK |
|---|---:|---:|---:|---:|---|
| edge-100-1-A / 91 | 7 | 9 | 10 | 0 | START |
| edge-100-1-A / 95 | 8 | 10 | 11 | 0 | ILLEGAL_NODE |
| edge-100-2-B / 56 | 9 | 11 | 12 | 2 | START |
| edge-100-2-B / 60 | 10 | 12 | 13 | 2 | ILLEGAL_NODE |

## 失败、限制与证据

- 原 `edge-0-2-B` 在 P0A 登录等待 60 秒后超时，尚无 P0_EDGE_PLAN 或测量请求，清理进程后 server_exit=1；根因未查明，不声称是生产崩溃。保留失败 manifest，在新标签 `edge-0-2-B-startup2` 继续同槽位，参数和 jar 均不变。实际执行包含该额外启动失败，并非隐去失败后的“首次全绿”。
- `edge-0-1-A` trial=17、k=11：ACTION_ENDED_BEFORE_PRESS。`edge-100-1-A` trial=38、k=5：AGE_OVERSHOOT。两条未发包，缺少收包年龄等服务端值，如实留空；不追加尝试或调整时间。这限制完整重复数验收，但不改变已测 k=7→9 的晚到边界差异。
- 这批只测固定朝向、普通僵尸命中确认下的 light_1→light_2。没有证明其它动作边、低 TPS、恶意时间戳、更多延迟或真人体验的效果。此处 S08=0 是固定朝向扫点回归，不代替连续转鼠标验收。
- 所有实际发送的高端请求均拒绝，未发现越窗；不把漏发写成拒绝通过。不调整参数重跑，不改生产代码。

[汇总 JSON（完整八会话和失败启动、逐请求必要观测）](evidence/combo-boundary/summary.json) · [一页图](evidence/combo-boundary/boundary.png)。入库仅这两项证据；逐帧数据和原始进程日志留在忽略的 build/p0，不入库。

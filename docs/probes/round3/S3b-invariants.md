# S3b 补充：不变量通过，360条违例0

## 假设与做法

沿用已审核的延后2/3tick、窗口5tick、原命中tick冻结几何；按不变量验收，不再要求提前量桶配额或单一成功率。服务端与客户端均用 System.nanoTime；每条输入独立记录真实发送、完整payload到达、提交截止，避免日历毫秒和日志打印顺序判定边界。

独立 `tools/probes2/s3supp`。在已连接的真实 GuardB 网络频道前记录完整格挡payload并入意图 journal，不改数据包或世界；提交时在同一短锁下取得单调deadline和已到达意图，游戏线程执行格挡或原生伤害。锁使“到达登记”和“提交截断”具有明确顺序；`arrival < deadline` 才有效。网络线程不结算伤害或回滚世界。原 FML handler 的额外主线程排队仍有诊断日志，但不是本轮到达定义。若仅等旧handler队列，会将完整网络输入已到、handler尚未消费的情况误判迟到，因此这一接入差异明确写出，不静默绕过。

时间戳由真实客户端发送时按同机服务端nano锚点/50ms换算，floor到tick；不是发送计划值，也不是生产安全校时。测试只有绑定玩家/固定合法频道的真实负载，未验证恶意payload、未来stamp、重放、跨机时钟原点等。geometry在命中tick固定，提交不重新选择目标或回滚世界。guard window判定为[hit-4,hit]。

## 命令与证据

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/s3supp/experiment.gradle s3suppLaunchFiles --no-configuration-cache
python tools/probes2/s3supp/run.py --accept-eula --label new-main --rtts 0,50,100,200 --repetitions 5 --legacy 0
python tools/probes2/s3supp/run.py --accept-eula --label new-upper --rtts 300,400 --repetitions 5 --legacy 0
python tools/probes2/s3supp/run.py --accept-eula --label new-edge --rtts 200 --repetitions 60 --legacy 0 --leads 1 --only-defer 2
python tools/probes2/s3supp/analyze.py --label new-main
python tools/probes2/s3supp/analyze.py --label new-upper
python tools/probes2/s3supp/analyze.py --label new-edge
```

新label；localhost25572/25573；用户已接受EULA。六档完整矩阵每档50次（2种延期×5计划提前量×5），另集中200ms延期2、计划提前0的60次。共360条，真实专用服与两个真实客户端。

[主矩阵](evidence/guard-nano1-invariants.json)、[高延迟](evidence/guard-upper2-invariants.json)、[200ms边界](evidence/guard-edge200-invariants.json)、[总计](evidence/guard-all-invariants.json)。每场目录 `per-input-nano.json` 列出**每条** stamp/hit、send_nano、arrival_nano、deadline_nano、纳秒先后、实际格挡/真实损血和违例；原始三端日志及命令/哈希也在同目录。

| RTT | 条数 | 窗口内且先到：成功 | 全部迟到：失败 | 违例 |
| --- | --- | --- | --- | --- |
| 0 | 50 | 48 | 0 | 0 |
| 50 | 50 | 49 | 1 | 0 |
| 100 | 50 | 50 | 0 | 0 |
| 200 | 50 | 46 | 4 | 0 |
| 300 | 50 | 34 | 16 | 0 |
| 400 | 50 | 25 | 25 | 0 |
| 200集中边界 | 60 | 1 | 59 | 0 |
| 总计 | 360 | 253/253 | 105/105 | **0** |

迟到中104条stamp仍在窗口内，全部失败；其余一条本身窗口外。另2条早到但stamp窗口外也失败。成功均损血0；失败调用真实EntityPlayerMP.attackEntityFrom，损血1。分析器独立拼接客户端SEND、服务器WIRE与RESULT，不用服务端自己打印的eligible作为唯一证据。

## 200ms同毫秒边界

旧第二轮id16只有currentTimeMillis，无法追溯恢复纳秒顺序，不伪造旧项的最终分类。本轮真实网络集中补测捕获以下**同一1ms单调时钟桶**中的截止前/后案例：

| id | stamp=hit | arrivalNano | deadlineNano | 余量 ms | 结果/损血 |
| --- | --- | --- | --- | --- | --- |
| 1 | 429 | 99386768134900 | 99386768207100 | +0.0722 | 成功/0 |
| 28 | 942 | 99412406723700 | 99412406627500 | -0.0962 | 失败/1 |
| 39 | 1151 | 99422854608400 | 99422854552900 | -0.0555 | 失败/1 |

日历日志与单调时钟桶原点不同；以以上nano排序，不用打印wall是否同字符串决定先后。边界来源完整记录在 [60条输入](evidence/guard-edge200-rtt200/per-input-nano.json)。300ms另有1个同ms桶项，合计4个。

## 延后量覆盖上限与反馈代价

每条可覆盖的实际单程上限是 `(deadlineNano-sendNano)/1e6`，严格小于才保证先到；它随按下相位、服务端tick实际节奏与输入提前量变化。合法stamp=hit的所测上限范围：延后2为 **46.8398–108.0681ms**，延后3为 **69.1323–148.0077ms**。这些是本轮样本界限，不是全场景最低保证。提前tick的输入还有额外余量，不能把同一上限用于所有stamp。

20TPS均匀推进且在原命中时刻立即发送的理想上限约100/150ms；在最后合法tick较晚发送，余量可能少近1tick。因此原型说明1.1/S3的“延后2约覆盖单程100ms”需要加相位/稳定TPS条件；不能理解为所有合法按键在100ms单程都成功。200ms RTT时延期2已出现迟到，延期3在本轮该档窗口内输入全部先到；300/400ms提供更高单程的负例。

从真实FREEZE到提交，延期2实测 **91.969–113.769ms**；延期3 **16.9108–159.1378ms**。16.91ms是200ms场id26的真实三逻辑tick间隔，保留不剔除；原版MinecraftServer 486–493行有积压tick连续执行循环，短墙钟间隔与该机制一致，但本轮未逐tick采样证明这次积压原因。该输入实际上较真实FREEZE早约126.49ms发送，说明同机锚点按50ms换算stamp在tick节奏变化时也有漂移。

代价是伤害、防御确认及由它们触发的首个命中反馈要等提交；客户端可先预测动作，不能提前确认伤害。以上测量是结算延后，不是端到端画面/音效延后，回程网络和渲染尚未单测。如果未来要求物理时间上的最低100/150ms等待，需要另审单调截止与逻辑tick双约束；本轮没有擅自采用。

## 构建、版本、限制与结论

矩阵jar `17c6606bb42750c4a851288a3df42acf37caf11a1cb5394432f98f5f64512a73`；集中边界增加子集夹具开关后jar `43573ba198d5d517b79f683ddb572eab294196bc3355a81ed7bfb8a06db17c59`。最终只修正辅助日志中nano字段的标签、移除失去意义的旧wireMs计算，final jar哈希及resolve的javap指令相同检查见总计JSON；未将最终日志清理冒称全矩阵重新运行。原矩阵辅助RECEIVE旧wireMs不作为测量，per-input只取SEND.nano/WIRE.nano/RESULT.deadlineNano。

初次300ms客户端未登录而超时，没有有效样本；本机线程栈未显示死锁。随后创建config目录并以新label完整复跑300/400ms，启动失败原因未单独证明，不把目录变更宣称根因修复。旧不完整世界保留本地、不纳入验收。所有7场最终有效专用服exit=0、实际客户端/OpenGL类加载0；原生启动、开发签名、版本检查、OpenAL警告保留。

**通过本轮不变量标准：360条违例0。** 用户最新审核口径优先于旧报告提前量桶配额；未重测已通过S4b/S5b，也没有恢复已作废的固定age=2说法。原型说明1.1/S3和network应明确到达截止的线性化接入、相位/真实tick节奏对覆盖范围的影响、以及结算反馈延后。跨机安全校时、一般几何、死亡/换维度、恶意或乱序网络、生产协议及P0尚未验证。

# S2：玩家位移 — 需改方案

## 假设与方法

按原型说明 1.1/S2，客户端通过真实 `moveEntity` 预测水平位移，服务端验证最大逐 tick 位移、实例/段序号以及两次位置间 AABB 扫掠。原生 C03 负责位置同步，不由服务端再叠加同一份自位移。夹具步长 0.4×5 tick（踏步）和 0.8×5 tick（突进），开放平台与 x=3 的实体墙各测试；另发送超过包络的 5 格位移。

原稿/原型说明未提供这两个自位移包络的完整距离数值；2/4 格是本次同步与碰撞的校准负载，步长和持续 tick 已配置化，不作为正式动作数据或平衡起点。固定的试验几何、伤害对照剂量和速度包样本同样不是冻结的玩法规则。

对照实际 `NetHandlerPlayServer.processPlayer`：原版调用 `moveEntity`，检查碰撞后的位置误差，并经 S08 纠正；速度平方阈值为 100，不等同于模组动作的最大位移。服务端测试显式关闭 creative/noClip，无瞬移放行。额外测试服务端设置 motionX=0.4、motionY=0.25、velocityChanged=true，经原生 S12 发送击退/浮空速度。

## 命令与实测输出

```text
python tools/probes/run_matrix.py --accept-eula --rtts 100,0,50,200,100:20 --label matrix1 --trials 12
python tools/probes/run_matrix.py --accept-eula --rtts 100 --label boundary2 --trials 6 --negative-cases
```

矩阵每场景 8 次合法、4 次超额请求；0/50/100/200ms 与 100ms＋20ms 抖动下，40 次合法移动的服务端与预测终点误差均为 0，位移期间 S08=0，校验拒绝=0。100ms 实際代理平均单向转发约 49.44ms；抖动场景约 47.28ms，调度误差/范围详见 summary。这些是水平夹具数据，不是持续战斗每分钟纠正频率。

代表输出：

```text
S2_END name=ProbeA lane=open speed=0.4000000059604645 valid=5 rejects=0 dx=2.0000000298023224 x=2.5000000298023224 expectedX=2.5000000298023224
S2_END name=ProbeA lane=open speed=0.800000011920929 valid=5 rejects=0 dx=4.000000059604645 x=4.500000059604645 expectedX=4.500000059604645
S2_END name=ProbeA lane=wall speed=0.800000011920929 valid=5 rejects=0 dx=2.199999988079071 x=2.699999988079071 expectedX=2.699999988079071
S2_VELOCITY_SENT x=0.4 y=0.25 origin=SERVER
S2_VELOCITY_RECEIVED x=0.4 y=0.25
```

实体半宽约 0.3，突进终点 x≈2.7，与 x=3 的墙接触，没有穿墙。超额自定义请求每场景都被拒绝并纠正；这是预期拒绝，不能计作合法预测的回弹。

补充负例保留原生 C03、故意省略全部自定义位移消息：

```text
S2_END name=ProbeA lane=open speed=0.4000000059604645 valid=0 rejects=0 dx=5.0 x=5.5 expectedX=0.5
S2_RESULT trial=0 predicted=5.5 server=5.5 validated=0.5 delta=0.0 rejects=0 S08=0 postMotionS08=0
```

## 结论与设计影响

客户端预测＋原生碰撞的可行性通过了所测水平场景；**单独校验自定义位移消息不能保证服务端权威**，未授权原生位置包可以绕过它。因此总体 S2 为“需改方案”，不是宣布客户端预测在 1.7.10 不可行，也不是把实现缺口冒充原版机制不支持。

建议补充原型说明 1.1/S2、network“Minecraft 原生移动同步”、collision“移动分类”三处：包络与扫掠必须约束最终被原生处理器接受的每次位置更新，不能形成互不约束的两套入口；缺失/重复动作消息不放行额外位移。下一轮可验证接入原生 C03 的逻辑线程处理前过滤/统一校验，或明确服务端位置提交与客户端和解的唯一入口。不要放宽 vanilla 的 moved-too-quickly/moved-wrongly 检查来掩盖缺口。这些替代接入未在本阶段实现。

楼梯、斜向/空中、液体、用户 WASD 与 Root Motion 叠加、击退与自位移同时发生、低 TPS/丢包及一分钟实际对战未验证。S12 已收到并实际推动玩家；没有宣称完整击退/浮空控制规则通过。数值保持配置起点。

## 审核后的限定

- 报告中的“墙拦截成立”测的是客户端自己的 `moveEntity` 碰撞加原版服务端复核，没有测“恶意或失步客户端发出穿墙位置包被服务端拦住”。后者仍未验证。
- 原生 C03 接受 5 格单包位移，与原版“单包位移平方上限 100”一致，属于已知的原版宽松度。
- 建议接入点：不钩包处理，在服务端 tick 结束时用 `player.posX/Z` 的实际变化对照动作位移包络，超出则 S08 拉回。所有原生位置包最终都落在这个位置上。该方案尚未验证。

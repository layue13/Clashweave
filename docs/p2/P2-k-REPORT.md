# P2-k 挂点通用化(计划 B2)

状态:已实现并做了实机检查。**挂点的位置与角度只是起点,外观调整由用户后续自己做。**

## 1 做了什么
资源数据 `mounts[]` 本来就是通用格式(`slot,x,y,z,angle`),但渲染把槽写死成 `left_hip`,在数据里写别的槽会被读入后忽略。现在:
- `core/MountSlots`:槽位表 = 骨架上固定的锚点。`left_hip`(骨盆,与以前完全相同)、`right_hip`(右腿枢轴,与左髋镜像的同一变换)、`back`(躯干:`bipedBody.postRender`,随躯干的转、倾、侧弯)。新增锚点需代码,在已有锚点上新增挂点只需数据。
- 资源新增可选字段 `stowedSlot`:鞘挂在哪个槽,缺省 `left_hip`(现有 katana 数据一字未改)。
- 加载时校验(`ContentRegistry`):`mounts[].slot` 必须是已知槽;`stowedSlot` 必须是已知槽且该资源的 `mounts` 里有同名条目。
- 渲染:`PresentationAssets.Render` 记住所选槽;`WeaponRendering.placeMount` 按槽移动到锚点(第三人称与观察者);第一人称的腰部绘制(`ownWaist`)对 `back` 不画(自己看不到)。
- 单测:`ContentRegistryTest.mountSlotsAreKnownAndStowedSlotNamesOneOfTheResourcesMounts`(已知槽、未知挂点槽被拒、未知 `stowedSlot` 被拒、`stowedSlot` 指向不存在的挂点被拒);全部测试通过。

## 2 实机
- `p2mount-back-102230`:`--wf1c weapon2 --content-pack tools/p2/mount-back ...`(WF-1c 的薙刀数据包,只加了 `back` 挂点与 `stowedSlot: back`,x 0、y 0.30、z 0.22、angle 100)。`weapon2-actions-and-chain`、`weapon2-damage-multiplier` 仍通过(passed=2 failed=0)。截图 `docs/p2/p2k-shots/back-side.png`(持有者侧视:鞘斜着露在肩后)、`back-observer.png`(观察者视角:鞘在背后,随身体转向)。
- `p2mount-hip-102335`:默认数据的刀,`--pose-verify 90`,`docs/p2/p2k-shots/hip-katana-regression.png`:鞘仍在左髋,与此前的照片一致。

## 3 未验证 / 限制
- 背部鞘的位置、角度没有调:鞘从肩后斜着伸出,角度明显不是好看的值(数据可调,不是代码问题)。
- 只拍了侧视与一个观察者角度;**没有拍正面与背面**,没有检查穿模。
- `right_hip` 没有实机拍照(只有同一变换的镜像代码与校验);第一人称"背部不画"没有实拍。
- 蹲下、游泳、骑乘时背部鞘的跟随没有验证(髋部有蹲下补偿,背部直接随躯干)。
- 没有为新槽做模型:测试复用刀的鞘模型。

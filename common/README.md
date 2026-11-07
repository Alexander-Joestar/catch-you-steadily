# 救援配置与构建

服务端启动时读取工作目录的 `config/catch-you-steadily.properties`，文件不存在时创建默认配置。修改配置后需重启服务端；布尔字段只接受 `true` 或 `false`（不区分大小写），非法值会导致配置加载失败。已有配置文件缺少新字段时使用默认值。

- `exclude-hostile=false`：默认包含敌对生物；改为 `true` 后排除敌对生物。
- `excluded-entities=`：按逗号分隔填写实体注册 ID，例如 `minecraft:creeper,minecraft:spider`。
- `attraction-radius=24`：预测落点周围的水平搜寻半径，允许 1 到 32 格。
- `vertical-range=12`：预测落点上下的搜寻范围，允许 1 到 16 格。
- `keep-riding=false`：默认在坐骑安全落地后自动解除骑乘；设为 `true` 时安全落地后保持玩家与生物的骑乘关系。救援会话和临时目标仍会清理，摔落距离会清零。

只对可能造成摔落伤害、预测路径未经过液体且最终会碰撞方块的玩家启动救援。服务端每 tick 遍历在线玩家，仅读取位置与 fallDistance；仅离地玩家和当前救援任务维护相邻 tick 位置样本，只有检测到向下位移时才执行最多 100 步轨迹模拟。每次救援开始和落点水平移动至少 3 格、垂直移动至少 2 格时，才查询落点范围内的已加载生物；默认搜索框是 48×24×48 格，配置上限对应 64×32×64 格。不会遍历全世界生物或用视线射线过滤。追随路径由 Mob 的 GoalSelector 推进，导航目标每 10 次 Goal 更新才重新计算；结束时只移除临时目标，原有目标保留。当玩家正在下落、预计在两个 tick 内到达落点附近生物的顶部且水平范围重叠时，服务端尝试建立乘客关系；只有骑乘成功才清零本次摔落距离。坐骑安全落地后按 `keep-riding` 选择解除或保持骑乘，不提供持续免伤。

在仓库根目录运行 `./gradlew :fabric1201:build :fabric1211:build :forge1201:build :neoforge1211:build buildLegacyVariants`，也可运行 `./gradlew buildReleaseJars`。旧版构建需要 JDK 25；任务会优先使用当前用户 `.jdks/jbr-25.0.4.1-1`，也可设置 `LEGACY_JAVA_HOME`。独立构建旧版时使用 `cd legacy && ./gradlew :forge1122:build :forge1710:build`。

六个发行 JAR 分别位于：

- `fabric/1.20.1/build/libs/catch-you-steadily-fabric-1.20.1-0.1.0.jar`
- `fabric/1.21.1/build/libs/catch-you-steadily-fabric-1.21.1-0.1.0.jar`
- `forge/1.20.1/build/libs/catch-you-steadily-forge-1.20.1-0.1.0.jar`
- `neoforge/1.21.1/build/libs/catch-you-steadily-neoforge-1.21.1-0.1.0.jar`
- `legacy/forge1122/build/libs/catch-you-steadily-forge-1.12.2-0.1.0.jar`
- `legacy/forge1710/build/libs/catch-you-steadily-forge-1.7.10-0.1.0.jar`

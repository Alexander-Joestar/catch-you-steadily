# 救援配置与构建

配置保存在游戏目录的 `config` 目录：Fabric 1.20.1 和 1.21.1 使用 `catch-you-steadily.json`；Forge 1.20.1 和 NeoForge 1.21.1 使用 `catch_you_steadily-common.toml`；Forge 1.7.10 和 1.12.2 使用 `catch-you-steadily.cfg`。文件不存在时由相应加载器或模组生成默认配置。Forge 和 NeoForge 在存在同名默认模板时会使用该模板。旧的 properties 文件不会自动读取或转换，请手动将设置复制到新配置文件。

配置在服务器启动时读取为不可变快照，修改后需重启服务器才能生效。Forge 和 NeoForge 的原生文件监听器可能在运行时读取并修正文件，但不会更新当前救援快照。旧版 Forge 遇到无法解析的 CFG 时会记录错误并将原文件重命名为 `.errored` 备份，启动会失败。布尔值、数值范围和实体列表无效时记录错误并拒绝初始化；缺少字段时使用默认值。

- `exclude-hostile`：默认 `false`，设为 `true` 后排除敌对生物。
- `excluded-entities`：实体注册 ID 列表，例如 `minecraft:creeper` 和 `minecraft:spider`。JSON 和 TOML 使用字符串数组，CFG 使用 Forge 的字符串列表。
- `attraction-radius`：默认 24，预测目标位置周围的水平搜寻半径，允许 1 到 32 格。
- `vertical-range`：默认 12，预测目标位置上下的搜寻范围，允许 1 到 16 格。
- `keep-riding`：默认 `false`，在坐骑安全落地后自动解除骑乘；设为 `true` 时安全落地后保持玩家与生物的骑乘关系。救援会话和临时目标仍会清理，摔落距离会清零。

只对可能造成摔落伤害、预测路径未经过液体且最终会碰撞方块的玩家启动救援。服务端每 tick 遍历在线玩家，离地玩家和当前救援会话维护位置样本；向下移动时最多模拟 100 步轨迹。水平预测使用当前速度和惯性估计，无法提前获知玩家松开按键或改变方向。

救援开始以及目标位置水平移动至少 2 格或垂直移动至少 2 格时，查询目标范围内的已加载生物；没有候选时至少间隔 10 tick 才再次查询。默认搜索框是 48×24×48 格，配置上限对应 64×32×64 格。生物使用原版导航，速度系数为 1.45。每只生物只归属于一个救援会话，会话结束或生物不再符合条件时移除对应目标。优先让较近的候选前往预测位置，其余候选按最大体宽分配网格目标；地形、寻路和实体碰撞仍可能妨碍生物到达目标。

玩家下落时检查玩家与生物的相对运动和方块碰撞；摔落事件还会检查玩家是否实际与生物顶部重叠。只有骑乘成功才清零本次摔落距离。坐骑安全落地后按 `keep-riding` 选择解除或保持骑乘，不提供持续免伤。

在仓库根目录运行 `./gradlew :fabric1201:build :fabric1211:build :forge1201:build :neoforge1211:build buildLegacyVariants`，也可运行 `./gradlew buildReleaseJars`。旧版构建需要 JDK 25；任务会优先使用当前用户 `.jdks/jbr-25.0.4.1-1`，也可设置 `LEGACY_JAVA_HOME`。独立构建旧版时使用 `cd legacy && ./gradlew :forge1122:build :forge1710:build`。

六个发行 JAR 分别位于：

- `fabric/1.20.1/build/libs/catch-you-steadily-fabric-1.20.1-0.1.0.jar`
- `fabric/1.21.1/build/libs/catch-you-steadily-fabric-1.21.1-0.1.0.jar`
- `forge/1.20.1/build/libs/catch-you-steadily-forge-1.20.1-0.1.0.jar`
- `neoforge/1.21.1/build/libs/catch-you-steadily-neoforge-1.21.1-0.1.0.jar`
- `legacy/forge1122/build/libs/catch-you-steadily-forge-1.12.2-0.1.0.jar`
- `legacy/forge1710/build/libs/catch-you-steadily-forge-1.7.10-0.1.0.jar`

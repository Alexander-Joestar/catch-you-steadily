# 旧版 Forge 构建

此目录是独立 Gradle 构建，使用 `legacy/gradlew` 固定 Gradle 9.7.1。根目录 Wrapper 固定 Gradle 8.8，供 Fabric、现代 Forge、NeoForge 和 common 使用。

在仓库根目录执行：

```sh
cd legacy
./gradlew :forge1122:build
./gradlew :forge1710:build
```

Windows 命令提示符将 `./gradlew` 替换为 `gradlew.bat`。RFG 2.0.2 使用 Java 25 class-file，Gradle 进程需运行于 JDK 25，例如 IntelliJ JBR 25。Forge 1.7.10 的反编译任务另需 Java 17 toolchain；Gradle 会探测已安装的 JDK，若未探测到已安装的 JDK 17，应在 Gradle toolchain 配置中提供该 JDK 路径。Forge 1.12.2 不需要 Java 17 toolchain。

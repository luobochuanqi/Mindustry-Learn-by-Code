# Mindustry Mod

这是一个基于 **Minecraft 1.21.1** 的 **NeoForge** 模组项目，使用 **Kotlin** 和 **Java** 混合开发，旨在实现类似 Mindustry
游戏中的机器和材料系统。

## 项目信息

- **模组ID**: `mindustry`
- **Minecraft版本**: 1.21.1
- **模组加载器**: NeoForge 21.1.209
- **编程语言**: Kotlin 2.2.20 + Java 21
- **构建工具**: Gradle 8.8

## 核心功能

### 已实现功能

1. **材料系统**: 实现了20种Mindustry风格的材料物品
2. **基础机器框架**: 提供可扩展的机器基类系统
3. **窑炉机器**: 将铅和沙子合成为金属玻璃的生产机器
4. **电力节点系统**: 支持能量存储和传输的电力方块
5. **多块结构框架**: 基础的多块结构系统框架

### 技术特性

- 基于 Forge Energy API 的能量系统
- 使用 LDLib2 + Yoga 的现代化UI框架
- 支持 JEI (Just Enough Items) 物品查看
- 集成 Jade 游戏内信息显示
- 使用 GeckoLib 3D动画库

## 环境要求

- **JDK**: 21
- **Gradle**: 8.8 (通过 wrapper 自动下载)
- **Minecraft**: 1.21.1

## 构建与运行

### 构建模组
```shell
gradle build
```

## 依赖库

- **Kotlin for Forge**: 5.10.0
- **GeckoLib**: 4.8.1 (3D动画)
- **JEI**: 19.25.0.323 (物品查看)
- **Jade**: 15.10.3 (信息显示)
- **LDLib2**: 2.1.7.a (UI框架)
- **Yoga**: 1.0.0 (布局引擎)

## 开发状态

项目目前处于开发阶段，已实现基础框架和部分功能，更多机器和系统正在开发中。

## 许可证

本项目采用 GNU GPL 3.0 许可证。

## 相关链接

- NeoForge: https://neoforged.net/
- Kotlin for Forge: https://thedarkcolour.github.io/KotlinForForge/
- 项目仓库: git@github.com:luobochuanqi/Mindustry-Learn-by-Code.git
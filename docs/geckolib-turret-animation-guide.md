# GeckoLib 炮台动画实现完整指南

本文档详细记录如何在 Minecraft NeoForge + Kotlin 环境中使用 GeckoLib 4 实现代码驱动的炮台瞄准动画。

## 目录

1. [架构概述](#架构概述)
2. [模型准备](#模型准备)
3. [代码实现](#代码实现)
4. [常见问题与解决方案](#常见问题与解决方案)
5. [完整代码示例](#完整代码示例)

---

## 架构概述

### 组件关系图

```
DuoTurretBlock (BaseEntityBlock)
    ↓ 创建
DuoTurretBlockEntity (ItemTurretBlockEntity + GeoBlockEntity)
    ↓ 每 tick 更新角度
BaseTurretBlockEntity (currentRotation, currentPitch)
    ↓ 网络同步
DuoRenderer (GeoBlockRenderer)
    ↓ 设置骨骼旋转
duo.geo.json (turret, up 骨骼)
```

### 关键类职责

| 类                       | 职责                              |
|-------------------------|---------------------------------|
| `DuoTurretBlock`        | 方块定义，注册 BlockEntityTicker 和渲染形状 |
| `DuoTurretBlockEntity`  | 实现 `GeoBlockEntity`，处理游戏逻辑      |
| `DuoRenderer`           | 渲染器，每帧设置骨骼旋转角度                  |
| `DuoModel`              | 绑定 Geckolib 资源文件路径              |
| `BaseTurretBlockEntity` | 基类，计算和存储瞄准角度                    |

---

## 模型准备

### Blockbench 模型要求

1. **骨骼命名规范**
    - `turret`: 控制水平旋转的骨骼（Yaw）
    - `up`: 控制垂直俯仰的骨骼（Pitch），作为 `turret` 的子骨骼
    - `base`: 不旋转的底座部分

2. **初始朝向**
    - 在 Blockbench 中，模型应朝向 **北（-Z 方向）**
    - 如果模型初始朝向有其他方向，需要在代码中补偿

3. **骨骼层级示例**

```
root
├── base (底座，不旋转)
└── turret (水平旋转骨骼)
    ├── up (垂直俯仰骨骼)
    │   ├── left2 (左炮管)
    │   └── right2 (右炮管)
    ├── down (下部结构)
    └── edge (边缘结构)
        ├── left
        ├── right
        ├── front
        └── behind
```

### 模型文件示例 (duo.geo.json)

```json
{
  "format_version": "1.12.0",
  "minecraft:geometry": [
    {
      "description": {
        "identifier": "geometry.duo",
        "texture_width": 64,
        "texture_height": 64
      },
      "bones": [
        {
          "name": "turret",
          "pivot": [
            0,
            9,
            0
          ],
          "rotation": [
            0,
            180,
            0
          ]
        },
        {
          "name": "up",
          "parent": "turret",
          "pivot": [
            8,
            0,
            -8
          ],
          "cubes": [
            ...
          ]
        },
        {
          "name": "base",
          "pivot": [
            0,
            0,
            0
          ],
          "cubes": [
            ...
          ]
        }
      ]
    }
  ]
}
```

**注意**：`turret` 骨骼的 `rotation: [0, 180, 0]` 表示模型初始朝向南，需要在代码中补偿。

---

## 代码实现

### 第一步：BlockEntity 实现 GeoBlockEntity

```kotlin
package xyz.luobo.mindustry.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.turret.entity.ItemTurretBlockEntity

class DuoTurretBlockEntity(
    pos: BlockPos,
    state: BlockState
) : ItemTurretBlockEntity(
    ModBlockEntityTypes.DUO_BLOCK_ENTITY.get(),
    pos,
    state
), GeoBlockEntity {

    // Geckolib 4 实例缓存 - 必须
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        // 代码驱动动画不需要注册控制器
        // 如果有关键帧动画（如后坐力），可在此处注册
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache
}
```

**关键要点**：

- 必须实现 `GeoBlockEntity` 接口
- 使用 `GeckoLibUtil.createInstanceCache(this)` 创建实例缓存
- `registerControllers` 可以为空（纯代码驱动动画）

---

### 第二步：Block 继承 BaseEntityBlock

```kotlin
package xyz.luobo.mindustry.common.turrets

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes

class DuoTurretBlock : BaseEntityBlock(
    Properties.of()
        .strength(2.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()  // **必须：避免遮挡问题，否则可能导致渲染异常或闪烁**
) {

    companion object {
        val CODEC: MapCodec<DuoTurretBlock> = simpleCodec { DuoTurretBlock() }

        // BlockEntityTicker - 必须注册，否则 BlockEntity 不会 tick
        private val TICKER: BlockEntityTicker<DuoTurretBlockEntity> =
            BlockEntityTicker { level, pos, state, blockEntity ->
                if (!level.isClientSide && blockEntity is DuoTurretBlockEntity) {
                    blockEntity.tickServer(level, pos, state)
                }
            }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return DuoTurretBlockEntity(pos, state)
    }

    /**
     * 关键：返回 ENTITYBLOCK_ANIMATED 以启用 BlockEntityRenderer
     */
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    /**
     * 关键：注册 BlockEntityTicker，使炮台能够每 tick 更新
     */
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (type === ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()) {
            @Suppress("UNCHECKED_CAST")
            TICKER as BlockEntityTicker<T>
        } else {
            null
        }
    }
}
```

**关键要点**：

- 必须继承 `BaseEntityBlock` 而不是 `Block`
- `getRenderShape()` 必须返回 `RenderShape.ENTITYBLOCK_ANIMATED`
- `getTicker()` 必须注册，否则 `BlockEntity` 不会 tick
- `.noOcclusion()` 避免渲染遮挡问题

---

### 第三步：Renderer 实现

```kotlin
package xyz.luobo.mindustry.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.turrets.DuoTurretBlockEntity

class DuoRenderer(context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<DuoTurretBlockEntity>(DuoModel()) {

    /**
     * 重写 render 方法以正确处理光照
     */
    override fun render(
        animatable: DuoTurretBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // 重新计算方块位置的光照值，解决黑暗问题
        val blockPos = animatable.blockPos
        val level = animatable.level

        val lightColor = if (level != null) {
            net.minecraft.client.renderer.LevelRenderer.getLightColor(level, blockPos)
        } else {
            packedLight
        }

        super.render(animatable, partialTick, poseStack, bufferSource, lightColor, packedOverlay)
    }

    /**
     * 在渲染前设置骨骼旋转
     * 这是代码驱动动画的核心
     */
    override fun preRender(
        poseStack: PoseStack,
        animatable: DuoTurretBlockEntity,
        model: software.bernie.geckolib.cache.`object`.BakedGeoModel,
        bufferSource: MultiBufferSource?,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        // 获取骨骼
        val turretBone = model.getBone("turret").orElse(null)
        val upBone = model.getBone("up").orElse(null)

        // 设置水平旋转 (Yaw)
        if (turretBone != null) {
            // 负号反转方向，确保目标向左时炮台也向左
            val yawRad = Math.toRadians((-animatable.currentRotation).toDouble()).toFloat()
            turretBone.setRotY(yawRad)
        }

        // 设置垂直俯仰 (Pitch)
        if (upBone != null) {
            // 正号确保目标向下时炮台也向下
            val pitchRad = Math.toRadians(animatable.currentPitch.toDouble()).toFloat()
            upBone.setRotX(pitchRad)
        }

        super.preRender(
            poseStack, animatable, model, bufferSource, buffer,
            isReRender, partialTick, packedLight, packedOverlay, colour
        )
    }
}

/**
 * 模型类 - 绑定 Geckolib 资源
 */
class DuoModel : GeoModel<DuoTurretBlockEntity>() {

    companion object {
        val MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "geo/duo.geo.json")
        val TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "textures/block/duo.png")
        val ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "animations/duo.animation.json")
    }

    override fun getModelResource(animatable: DuoTurretBlockEntity) = MODEL_RESOURCE
    override fun getTextureResource(animatable: DuoTurretBlockEntity) = TEXTURE_RESOURCE
    override fun getAnimationResource(animatable: DuoTurretBlockEntity) = ANIMATION_RESOURCE
}
```

**关键要点**：

- 继承 `GeoBlockRenderer<YourBlockEntity>`
- 在 `preRender()` 中设置骨骼旋转（不是 `renderRecursively`）
- 角度转弧度：`Math.toRadians(degrees).toFloat()`
- 使用 `setRotX()` / `setRotY()` 而不是直接赋值 `rotX` / `rotY`
- 重写 `render()` 修复光照问题

---

### 第四步：注册 Renderer

在 `EventHandler.kt` 中注册：

```kotlin
@SubscribeEvent
fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
    event.registerBlockEntityRenderer(
        ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
    ) { context ->
        xyz.luobo.mindustry.client.renderers.DuoRenderer(context)
    }
}
```

---

## 常见问题与解决方案

### 1. 显示黑紫块（Missing Texture）

**原因**：

- Block 继承 `Block` 而不是 `BaseEntityBlock`
- `getRenderShape()` 未返回 `ENTITYBLOCK_ANIMATED`

**解决**：

```kotlin
class DuoTurretBlock : BaseEntityBlock(...) {
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }
}
```

---

### 2. 炮台不旋转（不瞄准目标）

**原因**：

- 未注册 `BlockEntityTicker`，`BlockEntity` 不会 tick

**解决**：

```kotlin
override fun <T : BlockEntity> getTicker(
    level: Level,
    state: BlockState,
    type: BlockEntityType<T>
): BlockEntityTicker<T>? {
    return if (type === ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()) {
        TICKER as BlockEntityTicker<T>
    } else null
}
```

---

### 3. 旋转方向相反

**原因**：坐标系方向不匹配

**解决**：在 Renderer 中添加负号

```kotlin
// 水平旋转取反
val yawRad = Math.toRadians((-animatable.currentRotation).toDouble()).toFloat()

// 俯仰角取反（根据实际需要）
val pitchRad = Math.toRadians(animatable.currentPitch.toDouble()).toFloat()
```

---

### 4. 炮口朝向错误（屁股对目标）

**原因**：模型初始旋转与代码计算不匹配

**解决**：

- 检查模型 JSON 中的 `rotation` 值
- 在代码中补偿初始旋转
- 或在 Blockbench 中调整模型朝向

---

### 5. 渲染太暗

**原因**：光照值计算不正确

**解决**：重写 `render()` 方法，使用 `LevelRenderer.getLightColor()`

```kotlin
override fun render(...) {
    val lightColor = if (level != null) {
        LevelRenderer.getLightColor(level, blockPos)
    } else packedLight
    super.render(..., lightColor, ...)
}
```

---

### 6. 模型闪烁或渲染异常

**原因**：

- 在 `renderRecursively` 中修改骨骼导致递归问题
- `.noOcclusion()` 未添加

**解决**：

- 使用 `preRender()` 而不是 `renderRecursively()`
- 添加 `.noOcclusion()` 到 Block Properties

---

## 完整代码示例

### 文件结构

```
src/main/
├── java/xyz/luobo/mindustry/
│   ├── common/turrets/
│   │   ├── DuoTurretBlock.kt      # 方块定义
│   │   └── DuoTurret.kt           # BlockEntity
│   ├── client/renderers/
│   │   └── DuoRenderer.kt         # 渲染器
│   └── EventHandler.kt            # 渲染器注册
└── resources/assets/mindustry/
    ├── geo/duo.geo.json           # 模型文件
    ├── textures/block/duo.png     # 纹理文件
    └── animations/duo.animation.json  # 动画文件（可为空）
```

### 资源路径

确保资源路径与代码中的 `ResourceLocation` 匹配：

- 模型: `assets/<modid>/geo/duo.geo.json`
- 纹理: `assets/<modid>/textures/block/duo.png`
- 动画: `assets/<modid>/animations/duo.animation.json`

---

## 调试技巧

1. **查看骨骼名称**
   ```kotlin
   model.bones.forEach { (name, bone) ->
       println("Bone: $name")
   }
   ```

2. **验证角度值**
   ```kotlin
   println("Rotation: ${animatable.currentRotation}, Pitch: ${animatable.currentPitch}")
   ```

3. **检查 BlockEntity 是否 tick**
   在 `tickServer()` 中添加日志输出

4. **使用 F3+B 查看碰撞箱**
   确认 BlockEntity 存在且位置正确

---

## 最佳实践

1. **骨骼命名**：使用清晰的名称（`turret`, `barrel`, `base`）
2. **层级结构**：将需要一起旋转的部分设为父子关系
3. **初始朝向**：在 Blockbench 中统一朝向北方（-Z）
4. **纹理尺寸**：使用 2 的幂次方（16x16, 32x32, 64x64, 128x128）
5. **性能优化**：避免在 `preRender` 中创建新对象

---

## 参考文档

- [GeckoLib 官方文档](https://wiki.geckolib.com/)
- [Blockbench 下载](https://www.blockbench.net/)
- [NeoForge 文档](https://docs.neoforged.net/)

---

**文档版本**: 1.0  
**最后更新**: 2026-03-11  
**作者**: iFlow CLI

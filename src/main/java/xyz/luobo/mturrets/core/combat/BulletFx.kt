package xyz.luobo.mturrets.core.combat

/**
 * 子弹命中/到寿的客户端 FX 选型(Mindustry 对齐,见 docs/research/mindustry-bullet-fx.md)。
 *
 * 数据驱动:每个弹种定义 [BulletType.hitEffect] 与 [BulletType.despawnEffect],服务端在命中/到寿
 * 处把"选型+颜色"打包进 payload 发给客户端——客户端只消费解析好的描述符,不依赖弹种表
 * (命中同 tick 实体即 discard,同步字段竞态不可靠,见 #62 决策)。
 *
 * 上游语义:铜弹命中=hitBulletColor(渐隐环,消费 e.color),铅/玻璃命中=flakExplosion(固定三色
 * 爆团,忽略 e.color);到寿铜/玻璃=hitBulletColor、铅=hitBulletSmall。MTurrets 映射:
 * RING 消费 hitColor,FLAK 固定调色板,SMALL = 小型渐隐环(对位上游 hitBulletSmall)。
 */
enum class BulletFx {
    RING,
    FLAK,
    SMALL,
}

package xyz.luobo.mturrets.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.core.combat.BulletFx

/**
 * 子弹命中/到寿 FX 事件(#62):服务端在命中/到寿处显式发出,携带"选型+颜色+位置"。
 *
 * 不走同步实体字段的原因:命中同 tick 实体即 discard,此时写 SynchedEntityData 竞态/不可靠;
 * 在命中/到寿路径显式 send 一次 payload,顺序与可靠性都有保证,且守住 ADR-0010 零新增同步字段。
 * 客户端只消费解析好的描述符(不依赖弹种表),在 [pos] 放对应粒子。纯客户端表现,专用服务端不处理。
 */
data class BulletFxPayload(
    /** 特效选型(消费颜色与否由选型决定,见 [BulletFx]) */
    val fx: BulletFx,
    /** ARGB 颜色(int);仅 RING 消费,FLAK/SMALL 走固定调色板 */
    val color: Int,
    /** 世界坐标:命中点 / 子弹到寿位置 */
    val pos: Vec3
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<BulletFxPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BulletFxPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "bullet_fx"))

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BulletFxPayload> =
            StreamCodec.of(
                { buf, p ->
                    buf.writeShort(p.fx.ordinal)
                    buf.writeInt(p.color)
                    buf.writeDouble(p.pos.x)
                    buf.writeDouble(p.pos.y)
                    buf.writeDouble(p.pos.z)
                },
                { buf ->
                    val fx = BulletFx.entries[buf.readShort().toInt() and 0xFFFF]
                    val color = buf.readInt()
                    val x = buf.readDouble()
                    val y = buf.readDouble()
                    val z = buf.readDouble()
                    BulletFxPayload(fx, color, Vec3(x, y, z))
                }
            )
    }
}

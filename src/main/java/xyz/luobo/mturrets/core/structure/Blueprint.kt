package xyz.luobo.mturrets.core.structure

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/**
 * 结构的形状定义(ADR-0003):成员格相对锚点的偏移集 + 逐格外观。
 *
 * 偏移每轴限于 -1..1:成员格把偏移编进 blockstate 的整数属性,锚点坐标可由成员坐标减编码偏移重算,
 * 成员格不持久化任何引用,存档不存在主从失同步。因此蓝图跨距天花板是 3×3×3;
 * 需要更大结构时改为锚点 BE 反查表,再放开本约束。
 */
class Blueprint(val members: List<MemberSpec>) {
    init {
        require(members.all { it.offset.x in -1..1 && it.offset.y in -1..1 && it.offset.z in -1..1 }) {
            "member offsets must be within -1..1 per axis (3x3x3 blueprint ceiling), got $members"
        }
    }

    /**
     * 一格成员:相对锚点的偏移 + 外观方块。
     * 外观经惰性函数提供:注册期 DeferredBlock 尚未解析,取值推迟到成型时。
     */
    class MemberSpec(val offset: BlockPos, val stateProvider: () -> BlockState)
}
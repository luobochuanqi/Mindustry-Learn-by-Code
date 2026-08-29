package xyz.luobo.mturrets.core.structure

import net.minecraft.world.item.ItemStack
import xyz.luobo.mturrets.core.capability.IItemCapability

/**
 * 锚点 BE 契约(ADR-0003/0004):蓝图管线里所有锚点方块的 BlockEntity 共同接口。
 * 逻辑与状态只住锚点 BE;成员格无 BE,一切查询经此接口解析回锚点。
 */
interface BlueprintAnchor {
    /** 本结构当前生效的蓝图。 */
    val currentBlueprint: Blueprint

    /**
     * 结构拆除时散落的内容物。
     *
     * @param destroyed true = Health 归零路径:内容全毁,返回空。本期参数语义预留,
     * Health 结算(#31/#34)落地后由该路径调用;常规拆除(玩家挖/爆炸)传 false。
     */
    fun contentsToScatter(destroyed: Boolean): List<ItemStack>

    /** 成员格能力路由的物品入口(#32):成员面查询解析到锚点后返回此能力。 */
    val itemCapability: IItemCapability?
}
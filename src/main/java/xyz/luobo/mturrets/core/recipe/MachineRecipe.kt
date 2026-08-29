package xyz.luobo.mturrets.core.recipe

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import xyz.luobo.mturrets.common.ModRecipeTypes

/**
 * 机器工艺配方(ADR-0006):多入多出 + 耗时 + 配方级功耗,纯 datapack JSON
 * `{ingredients, results, processing_time, energy}`。`energy` 为整次加工的 FE 总耗
 * (#25 决议口径),BE 按进度均摊扣除。水等液体输入不在配方字段里,是机器语义(CONTEXT:Kiln)。
 */
class MachineRecipe(
    val ingredients: List<Ingredient>,
    val results: List<ItemStack>,
    val processingTime: Int,
    val energy: Int
) : Recipe<MachineRecipe.Input> {

    /** 以机器 Buffer 槽位序列为输入的匹配载体。 */
    class Input(val stacks: List<ItemStack>) : RecipeInput {
        override fun getItem(index: Int): ItemStack = stacks[index]
        override fun size(): Int = stacks.size
    }

    init {
        require(ingredients.isNotEmpty() && results.isNotEmpty()) {
            "machine recipe must have at least one ingredient and one result"
        }
    }

    /** 无序匹配:每个 Ingredient 需占用一个互不重复的满足槽位;允许多余存货。 */
    override fun matches(input: Input, level: Level): Boolean = assignSlots(input) != null

    /**
     * 贪心槽位分配:第 i 个 Ingredient 占一个未被占用且满足它的槽位;
     * 任一无配则返回 null。[matches] 与 [consume] 共用,匹配与扣除的槽位选择永不漂移。
     */
    private fun assignSlots(input: Input): IntArray? {
        val used = BooleanArray(input.size())
        val slots = IntArray(ingredients.size) { -1 }
        for ((index, ingredient) in ingredients.withIndex()) {
            for (i in 0 until input.size()) {
                if (!used[i] && ingredient.test(input.getItem(i))) {
                    used[i] = true
                    slots[index] = i
                    break
                }
            }
            if (slots[index] < 0) return null
        }
        return slots
    }

    /** 物品是否为该配方的原料之一(Buffer 准入拦截用)。 */
    fun matches(stack: ItemStack): Boolean = ingredients.any { it.test(stack) }

    /** 物品是否为该配方的产物之一(Buffer 准入与取出优先用)。 */
    fun produces(stack: ItemStack): Boolean =
        results.any { ItemStack.isSameItemSameComponents(it, stack) }

    /** 消耗匹配输入:每个 Ingredient 从其分配槽位扣 1 个单位。 */
    fun consume(input: Input) {
        val slots = assignSlots(input) ?: return
        for (slot in slots) input.getItem(slot).shrink(1)
    }

    override fun assemble(input: Input, registries: HolderLookup.Provider): ItemStack =
        results.first().copy()

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = true

    override fun getResultItem(registries: HolderLookup.Provider): ItemStack = results.first().copy()

    override fun getIngredients(): NonNullList<Ingredient> = NonNullList.copyOf(ingredients)

    override fun getSerializer(): RecipeSerializer<*> = ModRecipeTypes.MACHINE_SERIALIZER.get()

    override fun getType(): RecipeType<*> = ModRecipeTypes.MACHINE.get()

    /** datapack JSON 编解码器(注册于 ModRecipeTypes.MACHINE_SERIALIZER,ADR-0006 单 codec 家族)。 */
    class Serializer : RecipeSerializer<MachineRecipe> {
        override fun codec(): MapCodec<MachineRecipe> = CODEC
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> = STREAM_CODEC

        companion object {
            private val CODEC: MapCodec<MachineRecipe> = RecordCodecBuilder.mapCodec {
                it.group(
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(MachineRecipe::ingredients),
                    ItemStack.STRICT_CODEC.listOf().fieldOf("results").forGetter(MachineRecipe::results),
                    ExtraCodecs.POSITIVE_INT.fieldOf("processing_time").forGetter(MachineRecipe::processingTime),
                    Codec.INT.fieldOf("energy").forGetter(MachineRecipe::energy),
                ).apply(it, ::MachineRecipe)
            }

            private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> =
                StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    MachineRecipe::ingredients,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    MachineRecipe::results,
                    ByteBufCodecs.VAR_INT,
                    { r: MachineRecipe -> r.processingTime },
                    ByteBufCodecs.VAR_INT,
                    { r: MachineRecipe -> r.energy },
                    ::MachineRecipe,
                )
        }
    }
}

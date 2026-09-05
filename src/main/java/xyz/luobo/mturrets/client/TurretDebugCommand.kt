package xyz.luobo.mturrets.client

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.ClientCommandSourceStack
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.client.render.TurretDebugRenderer

/**
 * `/mturrets debug [on|off]`(#77):切换炮台 LOS 视线可视化。
 *
 * 走**客户端命令**注册,故不占服务端权限、非 OP 玩家亦可用;开关是纯客户端会话状态(不落盘)。
 * 无参 = 翻转,带参 = 显式设值。
 */
@EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.CLIENT])
object TurretDebugCommand {
    @SubscribeEvent
    fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("mturrets")
                .then(
                    Commands.literal("debug")
                        .executes { ctx -> set(ctx, !TurretDebugRenderer.enabled) }
                        .then(
                            Commands.argument("enable", BoolArgumentType.bool())
                                .executes { ctx -> set(ctx, BoolArgumentType.getBool(ctx, "enable")) }
                        )
                )
        )
    }

    private fun set(ctx: CommandContext<CommandSourceStack>, enable: Boolean): Int {
        TurretDebugRenderer.enabled = enable
        (ctx.source as? ClientCommandSourceStack)?.sendSuccess(
            {
                Component.translatable("mturrets.command.debug." + if (enable) "on" else "off")
                    .withStyle(if (enable) ChatFormatting.GREEN else ChatFormatting.GRAY)
            },
            false
        )
        return 1
    }
}

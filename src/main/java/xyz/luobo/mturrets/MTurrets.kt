package xyz.luobo.mturrets

import com.mojang.logging.LogUtils
import net.minecraft.client.gui.screens.Screen
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import org.slf4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import xyz.luobo.mturrets.EventHandler.ClientModEvents.onClientSetup
import xyz.luobo.mturrets.EventHandler.ServerModEvents.onDedicatedServerSetup
import xyz.luobo.mturrets.common.*


@Mod(MTurrets.MOD_ID)
object MTurrets {
    const val MOD_ID = "mturrets"

    var LOGGER: Logger = LogUtils.getLogger()

    init {
        LOGGER.info("Hello from MTurrets!")
        val MOD_CONTAINER: ModContainer = LOADING_CONTEXT.activeContainer

        // Register the traditional blocks first
        ModBlocks.register()
        ModItems.register()
        ModFluids.register()
        ModBlockEntityTypes.register()
        ModEntities.register()
        ModRecipeTypes.register()
        ModTabs.register()

        // 添加自定义物品栏标签
        MOD_BUS.addListener(ModTabs::addCreative)
        // 数据生成
        MOD_BUS.addListener(DataGen::generate)
        // 注册 Cap
        MOD_BUS.addListener(EventHandler::registerCapabilities)

        // 注册配置
        MOD_CONTAINER.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)

        // 用于 S/C 的分布式执行器
        runForDist(
            {
                MOD_BUS.addListener(::onClientSetup)
                MOD_CONTAINER.registerExtensionPoint(
                    IConfigScreenFactory::class.java,
                    object : IConfigScreenFactory {
                        override fun createScreen(
                            container: ModContainer,
                            parent: Screen
                        ): Screen {
                            return ConfigurationScreen(container, parent)
                        }
                    }
                )
            },
            {
                MOD_BUS.addListener(::onDedicatedServerSetup)
            })
}
}
package xyz.luobo.mindustry

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
import xyz.luobo.mindustry.EventHandler.ClientModEvents.onClientSetup
import xyz.luobo.mindustry.EventHandler.ServerModEvents.onDedicatedServerSetup
import xyz.luobo.mindustry.common.*
import xyz.luobo.mindustry.core.registry.MachineRegistry
import xyz.luobo.mindustry.core.registry.MultiblockRegistry


@Mod(Mindustry.MOD_ID)
object Mindustry {
    const val MOD_ID = "mindustry"

    var LOGGER: Logger = LogUtils.getLogger()

    init {
        LOGGER.info("Hello from Mindustry!")
        val MOD_CONTAINER: ModContainer = LOADING_CONTEXT.activeContainer

        // Register the traditional blocks first
        ModBlocks.register()
        ModItems.register()
        ModBlockEntityTypes.register()
        ModTabs.register()
        ModEntities.register()

        // 注册机器
        MachineRegistry.register()
        // 注册多块结构
        MultiblockRegistry.register()

        // 添加自定义物品栏标签
        MOD_BUS.addListener(ModTabs::addCreative)
        // 数据生成
        MOD_BUS.addListener(DataGen::generate)
        // 注册 Cap
        MOD_BUS.addListener(EventHandler::registerCapabilities)

        // 注册配置
        MOD_CONTAINER.registerConfig(ModConfig.Type.COMMON, Config.SPEC)
        MOD_CONTAINER.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)

        // 用于 S/C 的分布式执行器
        val obj = runForDist(
            clientTarget = {
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
            serverTarget = {
                MOD_BUS.addListener(::onDedicatedServerSetup)
            })

        println(obj)
    }
}
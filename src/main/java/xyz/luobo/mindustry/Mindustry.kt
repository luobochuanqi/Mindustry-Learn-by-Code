package xyz.luobo.mindustry

import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.neoforged.fml.common.Mod
import org.slf4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import xyz.luobo.mindustry.EventHandler.ClientModEvents.onClientSetup
import xyz.luobo.mindustry.EventHandler.ServerModEvents.onDedicatedServerSetup
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.ModBlocks
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.ModTabs

@Mod(Mindustry.MOD_ID)
object Mindustry {
    const val MOD_ID = "mindustry"

    var LOGGER: Logger = LogUtils.getLogger()

    init {
        LOGGER.info("Hello from Mindustry!")

        // Register the traditional blocks first
        ModBlocks.register()
        ModItems.register()
        ModBlockEntityTypes.register()
        ModTabs.register()

        // 添加自定义物品栏标签
        MOD_BUS.addListener(ModTabs::addCreative)
        MOD_BUS.addListener(DataGen::generate)

        // 用于 S/C 的分布式执行器
        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onDedicatedServerSetup)
                "test"
            })

        println(obj)
    }
}
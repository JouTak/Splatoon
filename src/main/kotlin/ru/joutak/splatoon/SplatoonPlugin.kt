package ru.joutak.splatoon

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import ru.joutak.splatoon.listeners.PlayerToggleSneakListener
import ru.joutak.splatoon.listeners.PlayerUseListener
import ru.joutak.splatoon.listeners.ProjectileHitListener
import ru.joutak.splatoon.scripts.AddNicksCommand
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

class SplatoonPlugin : JavaPlugin() {
    companion object {
        @JvmStatic
        lateinit var instance: SplatoonPlugin
    }

    public var mapName = "";
    public val boostLocations: MutableList<List<Double>> = mutableListOf()
    private var customConfig = YamlConfiguration()
    private fun loadConfig() {
        val fx = File(dataFolder, "config.yml")
        if (!fx.exists()) {
            saveResource("config.yml", true)
        }
        val config = config
        mapName = config.getString("map_name")!!
        val coordList = config.getList("boost_locations") ?: boostLocations

        for (item in coordList) {
            if (item is List<*>) {
                if (item.size == 3) {
                    try {
                        val x = (item[0] as Number).toDouble()
                        val y = (item[1] as Number).toDouble()
                        val z = (item[2] as Number).toDouble()

                        boostLocations.add(listOf(x, y, z))
                    } catch (e: Exception) {
                        logger.warning("Invalid coordinate format: $item")
                    }
                }
            }
        }
    }



    /**
     * Plugin startup logic
     */
    override fun onEnable() {
        instance = this

        loadConfig()

        Bukkit.getPluginManager().registerEvents(PlayerToggleSneakListener(), this)
        Bukkit.getPluginManager().registerEvents(PlayerUseListener(this), this)
        Bukkit.getPluginManager().registerEvents(ProjectileHitListener(), this)
        getCommand("splatoon")?.setExecutor(AddNicksCommand())
        getCommand("splatoon")?.tabCompleter = AddNicksCommand()
        logger.info("Плагин ${pluginMeta.name} версии ${pluginMeta.version} включен!")
    }

    /**
     * Plugin shutdown logic
     */
    override fun onDisable() {

    }
}

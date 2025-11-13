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
    private var customConfig = YamlConfiguration()
    private fun loadConfig() {
        val fx = File(dataFolder, "config.yml")
        if (!fx.exists()) {
            saveResource("config.yml", true)
        }
        val config = config
        mapName = config.getString("map_name")!!
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

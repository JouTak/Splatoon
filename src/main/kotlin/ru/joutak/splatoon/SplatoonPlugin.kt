package ru.joutak.splatoon

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ru.joutak.splatoon.listeners.PlayerToggleSneakListener
import ru.joutak.splatoon.listeners.PlayerUseListener
import ru.joutak.splatoon.listeners.ProjectileHitListener
import ru.joutak.minigames.MiniGamesCore
import ru.joutak.minigames.managers.MatchmakingManager
import ru.joutak.minigames.domain.GameInstanceConfig
import ru.joutak.splatoon.scripts.GameManager
import java.io.File

class SplatoonPlugin : JavaPlugin() {
    companion object {
        @JvmStatic
        lateinit var instance: SplatoonPlugin
    }

    var mapName = ""
    var lobbyName = ""
    val boostLocations: MutableList<List<Double>> = mutableListOf()
    private lateinit var customConfig: YamlConfiguration

    private fun loadConfig() {
        val fx = File(dataFolder, "config.yml")
        if (!fx.exists()) saveResource("config.yml", false)

        customConfig = YamlConfiguration.loadConfiguration(fx)
        mapName = customConfig.getString("map_name") ?: "sp_arena"
        lobbyName = customConfig.getString("lobby_name") ?: "world"

        val coordList = customConfig.getList("boost_locations") ?: emptyList<Any>()
        for (item in coordList) {
            if (item is List<*> && item.size == 3) {
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

    private fun loadArenas() {
        val arenasList = mutableListOf<GameInstanceConfig>()
        val arenas = customConfig.getMapList("arenas") ?: emptyList<Map<String, Any>>()
        for (arena in arenas) {
            val id = arena["id"] as? String ?: continue
            val world = arena["world"] as? String ?: continue
            val teamCount = (arena["teamCount"] as? Int) ?: 2
            val playersPerTeam = (arena["playersPerTeam"] as? Int) ?: 3
            arenasList.add(
                GameInstanceConfig(
                    id = id,
                    teamCount = teamCount,
                    playersPerTeam = playersPerTeam,
                    meta = mapOf("world" to world)
                )
            )
        }
        MatchmakingManager.loadInstances(arenasList)
    }

    override fun onEnable() {
        instance = this

        loadConfig()
        MiniGamesCore.initialize(this)
        loadArenas()

        Bukkit.getPluginManager().registerEvents(PlayerToggleSneakListener(), this)
        Bukkit.getPluginManager().registerEvents(PlayerUseListener(this), this)
        Bukkit.getPluginManager().registerEvents(ProjectileHitListener(), this)

        logger.info("Плагин ${pluginMeta.name} версии ${pluginMeta.version} включен!")

        var taskId = 0
        taskId = server.scheduler.runTaskTimer(this, Runnable {
            val instance = MatchmakingManager.pollReady()
            if (instance != null) {
                GameManager.createGame(instance)
                server.scheduler.cancelTask(taskId)
            }
        }, 20L, 20L).taskId
    }

    override fun onDisable() {

    }
}

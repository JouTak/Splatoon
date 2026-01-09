package ru.joutak.splatoon

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import ru.joutak.minigames.MiniGamesCore
import ru.joutak.minigames.domain.GameInstanceConfig
import ru.joutak.minigames.managers.MatchmakingManager
import ru.joutak.splatoon.commands.SplatoonCommand
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.listeners.BacillusHitListener
import ru.joutak.splatoon.listeners.BoostPickupListener
import ru.joutak.splatoon.listeners.PlayerSessionListener
import ru.joutak.splatoon.listeners.PlayerToggleSneakListener
import ru.joutak.splatoon.listeners.PlayerUseListener
import ru.joutak.splatoon.listeners.ProjectileHitListener
import ru.joutak.splatoon.scripts.GameManager

class SplatoonPlugin : JavaPlugin() {
    companion object {
        @JvmStatic
        lateinit var instance: SplatoonPlugin
    }

    lateinit var settings: SplatoonSettings
        private set

    var lobbyName: String = "world"
        private set

    private fun loadArenas() {
        val arenasList = mutableListOf<GameInstanceConfig>()
        val arenas = config.getMapList("arenas") ?: emptyList<Map<String, Any>>()

        for (arenaAny in arenas) {
            val arena = arenaAny as? Map<String, Any> ?: continue
            val id = arena["id"] as? String ?: continue
            val world = arena["world"] as? String ?: continue
            val teamCount = (arena["teamCount"] as? Int) ?: 2
            val playersPerTeam = (arena["playersPerTeam"] as? Int) ?: 3

            val arenaBoostLocations = SplatoonSettings.readCoordinateTriplesFromArena(arena, logger)
            val boostLocations = if (arenaBoostLocations.isNotEmpty()) arenaBoostLocations else settings.boosts.locations

            val meta = mutableMapOf<String, Any>("world" to world)
            if (boostLocations.isNotEmpty()) meta["boostLocations"] = boostLocations

            arenasList.add(
                GameInstanceConfig(
                    id = id,
                    teamCount = teamCount,
                    playersPerTeam = playersPerTeam,
                    meta = meta
                )
            )

            GameManager.registerTemplateWorld(world)
        }

        MatchmakingManager.loadInstances(arenasList)
    }

    override fun onEnable() {
        instance = this

        saveDefaultConfig()
        reloadConfig()

        settings = SplatoonSettings.load(config, logger)
        lobbyName = settings.lobbyWorld

        MiniGamesCore.initialize(this)
        loadArenas()

        GameManager.cleanupOrphanedWorlds()

        Bukkit.getPluginManager().registerEvents(PlayerToggleSneakListener(), this)
        Bukkit.getPluginManager().registerEvents(PlayerUseListener(this), this)
        Bukkit.getPluginManager().registerEvents(ProjectileHitListener(), this)
        Bukkit.getPluginManager().registerEvents(BacillusHitListener(this), this)
        Bukkit.getPluginManager().registerEvents(PlayerSessionListener(), this)
        Bukkit.getPluginManager().registerEvents(BoostPickupListener(this), this)

        val cmd = getCommand("splatoon")
        if (cmd != null) {
            val executor = SplatoonCommand(this)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        }

        logger.info("Плагин ${pluginMeta.name} версии ${pluginMeta.version} включен!")

        server.scheduler.runTaskTimer(this, Runnable {
            val ready = MatchmakingManager.pollReady()
            if (ready != null) {
                logger.info("Команды собрались!")
                GameManager.createGame(ready)
            }
        }, 20L, 20L)
    }

    override fun onDisable() {
        GameManager.shutdownAllGames()
    }
}

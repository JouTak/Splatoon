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
import ru.joutak.splatoon.listeners.DamageGuardListener
import ru.joutak.splatoon.listeners.NaturalRegenerationListener
import ru.joutak.splatoon.listeners.PlayerSessionListener
import ru.joutak.splatoon.listeners.PlayerToggleSneakListener
import ru.joutak.splatoon.listeners.PlayerUseListener
import ru.joutak.splatoon.listeners.ProjectileHitListener
import ru.joutak.splatoon.listeners.SplatGunBowListener
import ru.joutak.splatoon.listeners.SplatGunProtectionListener
import ru.joutak.splatoon.scripts.GameManager
import ru.joutak.splatoon.util.SplatoonAttributes
import java.io.File

class SplatoonPlugin : JavaPlugin() {
    companion object {
        @JvmStatic
        lateinit var instance: SplatoonPlugin
    }

    private fun loadConfig() {
        val fx = File(dataFolder, "config.yml")
        if (!fx.exists()) saveResource("config.yml", false)

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(fx)
        SplatoonSettings.load(config, logger)

        GameManager.registerTemplateWorld(SplatoonSettings.defaultTemplateWorld)
    }

    private fun loadArenas() {
        val arenasList = mutableListOf<GameInstanceConfig>()

        SplatoonSettings.arenas.forEach { arena ->
            arenasList.add(
                GameInstanceConfig(
                    id = arena.id,
                    teamCount = arena.teamCount,
                    playersPerTeam = arena.playersPerTeam,
                    meta = mapOf(
                        "world" to arena.templateWorld,
                        // MiniGamesAPI expands each config into a pool (parallel instances) using meta["pool_size"].
                        // This allows multiple matches on the same template world.
                        "pool_size" to arena.instances,
                        // Keep a stable arena id even if instance config ids change in future.
                        "arenaId" to arena.id
                    )
                )
            )
            GameManager.registerTemplateWorld(arena.templateWorld)
        }

        MatchmakingManager.loadInstances(arenasList)
    }

    override fun onEnable() {
        instance = this

        loadConfig()
        MiniGamesCore.initialize(this)
        loadArenas()

        GameManager.cleanupOrphanedWorlds()

        Bukkit.getPluginManager().registerEvents(PlayerToggleSneakListener(), this)
        Bukkit.getPluginManager().registerEvents(PlayerUseListener(this), this)
        Bukkit.getPluginManager().registerEvents(SplatGunBowListener(this), this)
        Bukkit.getPluginManager().registerEvents(ProjectileHitListener(), this)
        Bukkit.getPluginManager().registerEvents(DamageGuardListener(), this)
        Bukkit.getPluginManager().registerEvents(NaturalRegenerationListener(), this)
        Bukkit.getPluginManager().registerEvents(BacillusHitListener(this), this)
        Bukkit.getPluginManager().registerEvents(SplatGunProtectionListener(this), this)
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
            val instance = MatchmakingManager.pollReady()
            if (instance != null) {
                logger.info("Команды собрались!")
                GameManager.createGame(instance)
            }
        }, 20L, 20L)
    }

    override fun onDisable() {
        // На случай перезагрузки/disable во время натяжения лука.
        server.onlinePlayers.forEach { SplatoonAttributes.removeBowNoSlow(it) }
        GameManager.shutdownAllGames()
    }
}

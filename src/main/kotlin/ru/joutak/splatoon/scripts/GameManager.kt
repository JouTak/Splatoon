package ru.joutak.splatoon.scripts

import com.onarandombox.MultiverseCore.MultiverseCore
import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import org.bukkit.*
import org.bukkit.Bukkit.getWorld
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import ru.joutak.minigames.domain.GameInstance
import ru.joutak.minigames.managers.MatchmakingManager
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.config.SplatoonSettings
import java.io.File
import java.util.UUID

object GameManager {
    val playerGame = mutableMapOf<UUID, Game>()
    val arenas: MutableMap<String, World> = mutableMapOf()
    private val gamesByWorld: MutableMap<String, Game> = mutableMapOf()
    private val spectatingWorldByPlayer: MutableMap<UUID, String> = mutableMapOf()

    private val adminAmmoOverride: MutableMap<UUID, Pair<Int, Long>> = mutableMapOf()

    private val templateWorlds: MutableSet<String> = mutableSetOf()

    fun registerTemplateWorld(worldName: String) {
        templateWorlds.add(worldName)
    }

    fun sendToLobby(player: Player) {
        val lobbyWorld = Bukkit.getWorld(SplatoonSettings.lobbyWorldName)
        val spawn = lobbyWorld?.spawnLocation ?: Bukkit.getWorlds()[0].spawnLocation

        player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
        player.inventory.clear()
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.absorptionAmount = 0.0
        player.removePotionEffect(PotionEffectType.GLOWING)
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
        player.teleport(spawn)
    }

    fun removePlayerFromGame(uuid: UUID) {
        val game = playerGame.remove(uuid) ?: return

        // Keep player stats for end-of-match results, but exclude the player from active match logic.
        game.markPlayerLeft(uuid)
        game.commands.remove(uuid)
    }

    fun getGame(player: Player): Game? = playerGame[player.uniqueId]

    fun getActiveGames(): List<Game> = gamesByWorld.values.toList()

    fun getGameByWorld(worldName: String): Game? = gamesByWorld[worldName]

    fun getSpectatingGame(player: Player): Game? {
        val world = spectatingWorldByPlayer[player.uniqueId] ?: return null
        return gamesByWorld[world]
    }

    fun setSpectating(playerId: UUID, worldName: String) {
        spectatingWorldByPlayer[playerId] = worldName
    }

    fun clearSpectating(playerId: UUID) {
        spectatingWorldByPlayer.remove(playerId)
    }

    fun setAdminAmmoOverride(uuid: UUID, team: Int, durationMs: Long) {
        adminAmmoOverride[uuid] = team to (System.currentTimeMillis() + durationMs)
    }

    fun getAdminAmmoTeam(uuid: UUID, baseTeam: Int): Int {
        val override = adminAmmoOverride[uuid]
        if (override != null) {
            if (System.currentTimeMillis() < override.second) return override.first
            adminAmmoOverride.remove(uuid)
        }
        return baseTeam
    }

    fun isLikelyCloneWorld(worldName: String): Boolean {
        for (template in templateWorlds) {
            val prefix = "${template}_"
            if (worldName.startsWith(prefix)) {
                val suffix = worldName.removePrefix(prefix)
                if (suffix.toIntOrNull() != null) return true
            }
        }
        return false
    }

    fun cleanupOrphanedWorlds() {
        val loaded = Bukkit.getWorlds().map { it.name }.toSet()
        loaded.forEach { worldName ->
            if (isLikelyCloneWorld(worldName) && !arenas.containsKey(worldName)) {
                cleanupWorld(worldName)
            }
        }

        val container = Bukkit.getWorldContainer()
        val dirs = container.listFiles() ?: emptyArray()
        dirs.forEach { dir ->
            if (dir.isDirectory && isLikelyCloneWorld(dir.name) && !arenas.containsKey(dir.name)) {
                cleanupWorld(dir.name)
            }
        }
    }

    fun createGame(instance: GameInstance) {
        val multiverseCore = Bukkit.getPluginManager().getPlugin("Multiverse-Core") as? MultiverseCore
        if (multiverseCore == null) {
            SplatoonPlugin.instance.logger.severe("Multiverse-Core не найден. Невозможно создать игру.")
            return
        }

        val baseArenaId = (instance.config.meta["arenaId"] as? String) ?: instance.config.id
        val arenaSettings = SplatoonSettings.arenasById[baseArenaId]

        val templateWorldName = arenaSettings?.templateWorld
            ?: (instance.config.meta["world"] as? String)
            ?: SplatoonSettings.defaultTemplateWorld

        val template = Bukkit.getWorld(templateWorldName)
        if (template == null) {
            SplatoonPlugin.instance.logger.severe("Template world $templateWorldName not found")
            return
        }

        val mvWorld = multiverseCore.mvWorldManager.getMVWorld(template.name)
        if (mvWorld != null) {
            mvWorld.setTime("day")
            mvWorld.setEnableWeather(false)
            mvWorld.setDifficulty(Difficulty.PEACEFUL)
            mvWorld.setPVPMode(false)
            mvWorld.gameMode = GameMode.ADVENTURE
            mvWorld.hunger = true
            mvWorld.setAllowAnimalSpawn(false)
            mvWorld.setAllowMonsterSpawn(false)
            mvWorld.allowPortalMaking(AllowedPortalType.NONE)
        }

        val worldName = nextWorldName(templateWorldName)
        cleanupWorld(worldName)

        try {
            multiverseCore.mvWorldManager.cloneWorld(template.name, worldName)
        } catch (e: Exception) {
            SplatoonPlugin.instance.logger.severe("Не удалось клонировать мир $templateWorldName -> $worldName: ${e.message}")
            cleanupWorld(worldName)
            return
        }

        var world = getWorld(worldName)
        if (world == null) {
            try {
                world = Bukkit.createWorld(WorldCreator(worldName))
            } catch (_: Exception) {
            }
        }

        if (world == null) {
            SplatoonPlugin.instance.logger.severe("World clone $worldName failed")
            cleanupWorld(worldName)
            return
        }

        world.setGameRule(GameRule.FALL_DAMAGE, false)
        world.setGameRule(GameRule.DROWNING_DAMAGE, false)
        world.setGameRule(GameRule.FIRE_DAMAGE, false)

        arenas[worldName] = world

        val game = Game(worldName, baseArenaId, arenaSettings?.spawns ?: emptyMap())
        gamesByWorld[worldName] = game
        gamesByWorld[worldName] = game

        val playersToRemove = mutableListOf<Player>()
        val teamsSnapshot = instance.teams.map { it.toList() }

        teamsSnapshot.forEachIndexed { teamIndex, teamPlayers ->
            teamPlayers.forEach { player ->
                val bukkitPlayer = Bukkit.getPlayer(player.name) ?: return@forEach
                playerGame[bukkitPlayer.uniqueId] = game
                game.commands[bukkitPlayer.uniqueId] = teamIndex
                game.paintedPerson[bukkitPlayer.uniqueId] = 0
                playersToRemove.add(bukkitPlayer)
            }
        }

        playersToRemove.forEach { p ->
            try {
                instance.removePlayer(p)
            } catch (_: Exception) {
            }
        }

        game.startGame(worldName)
    }

    private fun nextWorldName(templateWorldName: String): String {
        var key = 1
        while (true) {
            val worldName = "${templateWorldName}_$key"
            if (!arenas.containsKey(worldName)) return worldName
            key++
        }
    }

    private fun cleanupWorld(worldName: String) {
        val loadedWorld = Bukkit.getWorld(worldName)
        if (loadedWorld != null) {
            loadedWorld.players.toList().forEach { player ->
                sendToLobby(player)
            }
            Bukkit.unloadWorld(loadedWorld, false)
        }

        val multiverseCore = Bukkit.getPluginManager().getPlugin("Multiverse-Core") as? MultiverseCore
        if (multiverseCore != null) {
            try {
                multiverseCore.mvWorldManager.deleteWorld(worldName)
            } catch (_: Exception) {
            }
        }

        File(Bukkit.getWorldContainer(), worldName).deleteRecursively()
    }

    fun deleteGame(worldName: String, game: Game) {
        // Restore and remove spectators BEFORE world cleanup so their inventories/locations are not wiped.
        runCatching { game.forceRemoveAllSpectators(forceLobby = true) }

        game.commands.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                MatchmakingManager.removePlayer(player)
            }
        }

        cleanupWorld(worldName)

        playerGame.entries.removeAll { it.value == game }
        arenas.remove(worldName)
        gamesByWorld.remove(worldName)
        spectatingWorldByPlayer.entries.removeAll { it.value == worldName }
    }

    fun shutdownAllGames() {
        val games = gamesByWorld.values.toSet()
        games.forEach { game ->
            game.shutdownGame()
            deleteGame(game.worldName, game)
        }

        cleanupOrphanedWorlds()
    }
}

package ru.joutak.splatoon.scripts

import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.MultiverseCore
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getWorld
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.World
import ru.joutak.minigames.domain.GameInstance
import ru.joutak.splatoon.SplatoonPlugin
import java.io.File
import java.util.UUID

object GameManager {
    val playerGame = mutableMapOf<UUID, Game>()
    val arenas: MutableMap<String, World> = mutableMapOf()

    fun createGame(instance: GameInstance) {
        val multiverseCore =
            Bukkit.getPluginManager().getPlugin("Multiverse-Core") as MultiverseCore

        // world template from instance config meta
        val templateWorldName = instance.config.meta["world"] as? String
            ?: SplatoonPlugin.instance.mapName

        val template = Bukkit.getWorld(templateWorldName)
            ?: throw IllegalStateException("Template world $templateWorldName not found")

        val mvWorld = multiverseCore.mvWorldManager.getMVWorld(template)
        mvWorld.setTime("day")
        mvWorld.setEnableWeather(false)
        mvWorld.setDifficulty(Difficulty.PEACEFUL)
        mvWorld.setPVPMode(false)
        mvWorld.gameMode = GameMode.ADVENTURE
        mvWorld.hunger = true
        mvWorld.setAllowAnimalSpawn(false)
        mvWorld.setAllowMonsterSpawn(false)
        mvWorld.allowPortalMaking(AllowedPortalType.NONE)

        var key = 1
        while (arenas.containsKey("${templateWorldName}_$key")) key++

        val worldName = "${templateWorldName}_$key"
        multiverseCore.mvWorldManager.cloneWorld(template.name, worldName)

        val world = getWorld(worldName)
            ?: throw IllegalStateException("World clone $worldName failed")

        world.setGameRule(GameRule.FALL_DAMAGE, false)
        world.setGameRule(GameRule.DROWNING_DAMAGE, false)
        world.setGameRule(GameRule.FIRE_DAMAGE, false)

        arenas[worldName] = world

        val game = Game(worldName)

        // используем команды из teams внутри GameInstance
        instance.teams.forEachIndexed { teamIndex, teamPlayers ->
            teamPlayers.forEach { player ->
                val bukkitPlayer = Bukkit.getPlayer(player.name) ?: return@forEach
                playerGame[bukkitPlayer.uniqueId] = game
                game.commands[bukkitPlayer.uniqueId] = teamIndex
                game.paintedPerson[bukkitPlayer.uniqueId] = 0
            }
        }

        game.startGame()
    }


    fun deleteGame(worldName: String, game: Game) {
        val multiverseCore =
            Bukkit.getPluginManager().getPlugin("Multiverse-Core") as MultiverseCore

        multiverseCore.mvWorldManager.deleteWorld(worldName)
        File(Bukkit.getWorldContainer(), worldName).deleteRecursively()

        playerGame.entries.removeAll { it.value == game }
        arenas.remove(worldName)
    }
}

package ru.joutak.splatoon.scripts

import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.MultiverseCore
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask
import ru.joutak.splatoon.SplatoonPlugin
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.math.min

object GameManager {
    val playerGame = mutableMapOf<UUID, Game>()
    val arenas: MutableMap<String, World> = mutableMapOf()
    fun createGame(players: List<UUID>) {
        val multiverseCore: MultiverseCore =
            Bukkit.getServer().pluginManager.getPlugin("Multiverse-Core") as MultiverseCore
        val template = Bukkit.getWorld(SplatoonPlugin.instance.mapName)
        val mvWorld = multiverseCore.mvWorldManager.getMVWorld(template)
        mvWorld.setTime("day")
        mvWorld.setEnableWeather(false)
        mvWorld.setDifficulty(Difficulty.NORMAL)
        mvWorld.setGameMode(GameMode.SURVIVAL)
        mvWorld.setPVPMode(false)
        mvWorld.hunger = true
        mvWorld.setAllowAnimalSpawn(true)
        mvWorld.setAllowMonsterSpawn(false)
        mvWorld.allowPortalMaking(AllowedPortalType.NONE)
        var key = 1
        while (arenas.containsKey("${SplatoonPlugin.instance.mapName}_${key}")) {
            key++
        }
        val worldName = "${SplatoonPlugin.instance.mapName}_${key}"
        multiverseCore.mvWorldManager.cloneWorld(template!!.name, worldName)
        arenas[worldName] = Bukkit.getWorld(worldName)!!
        val game = Game(worldName)
        players.forEach { player -> playerGame[player] = game
            game.commands += player to  Random.nextInt(0, 4)
            game.paintedPerson += player to 0
        }
        game.startGame()
    }
    fun deleteGame(worldName: String, game: Game){
        val multiverseCore: MultiverseCore =
            Bukkit.getServer().pluginManager.getPlugin("Multiverse-Core") as MultiverseCore
        multiverseCore.mvWorldManager.deleteWorld(worldName)
        File(Bukkit.getWorldContainer(), worldName).exists()
        playerGame.entries.removeAll { entry ->
            entry.value == game
        }
        arenas.remove(worldName)
    }
}

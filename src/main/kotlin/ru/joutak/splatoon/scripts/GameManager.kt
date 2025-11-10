package ru.joutak.splatoon.scripts

import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.MultiverseCore
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID

object GameManager {
    val playerGame = mutableMapOf<UUID, Game>()
    val arenas: MutableMap<String, World> = mutableMapOf()
    fun createGame(players: List<UUID>) {
        val multiverseCore: MultiverseCore =
            Bukkit.getServer().pluginManager.getPlugin("Multiverse-Core") as MultiverseCore
        val template = Bukkit.getWorld("world")
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
        val worldName = "world_${arenas.size + 1}"
        multiverseCore.mvWorldManager.cloneWorld(template!!.name, worldName)
        arenas[worldName] = Bukkit.getWorld(worldName)!!
        val game = Game(worldName)
        players.forEach { player -> playerGame[player] = game }
        game.commands += players[0] to 2
        game.paintedPerson += players[0] to 0
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

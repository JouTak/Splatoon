package ru.joutak.splatoon.scripts

import net.kyori.adventure.text.Component
import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.MultiverseCore
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.collections.Map
import java.util.UUID
import ru.joutak.splatoon.SplatoonPlugin

object Stats {
    val playerGame = mutableMapOf<UUID, Game>()
    fun createGame() {
        val arenas: MutableMap<String, World> = mutableMapOf()
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
    }
}

class Game(var worldName: String) {
    val paintedCommand: MutableMap<Int, Int> = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    var paintedPerson: MutableMap<UUID, Int> = mutableMapOf()
    val commandColors: Map<Int, Material> = mapOf(
        0 to Material.RED_CONCRETE,
        1 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        3 to Material.YELLOW_CONCRETE
    )
    var commands: Map<UUID, Int> = mutableMapOf()
    fun startGame() {
        commands.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.teleport(Bukkit.getWorld(worldName)!!.spawnLocation)
        }

        val item = ItemStack(Material.GOLDEN_SHOVEL, 1)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Сплат-пушка").color(TextColor.color(0xFF55FF))
        )
        meta.persistentDataContainer.set(
            NamespacedKey(SplatoonPlugin.instance, "splatGun"), PersistentDataType.BOOLEAN, true
        )
        item.itemMeta = meta
        commands.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.inventory?.addItem(item)
        }


    }
}
package ru.joutak.splatoon.scripts

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.commands.AdminItems
import ru.joutak.splatoon.config.SplatoonSettings

object LobbyDecorationManager {
    private val decorations = mutableListOf<ItemDisplay>()

    fun spawnAll(){
        removeAll()

        val world = Bukkit.getWorld(SplatoonSettings.lobbyWorldName)
        if (world == null) return

        for (decoration in SplatoonSettings.lobbyDecorationItemLocations){
            val x = decoration["x"] as? Double ?: continue
            val y = decoration["y"] as? Double ?: continue
            val z = decoration["z"] as? Double ?: continue
            val typeStr = decoration["type"] as? String ?: "bomb"

            val location = Location(world, x, y, z)
            val itemStack = when (typeStr.lowercase()) {
                "bomb" -> AdminItems.bomb(-1)
                "bacillus" -> AdminItems.bacillus(-1)
                "gun" -> AdminItems.gun(-1)
                else -> continue
            }

            val display = world.spawn(location, ItemDisplay::class.java).apply {
                setItemStack(itemStack)
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                addScoreboardTag("lobby_decoration")
            }
            decorations.add(display)
        }

        SplatoonPlugin.instance.logger.info("Spawned ${decorations.size} lobby decorations")
    }

    fun removeAll(){
        decorations.forEach { it.remove() }
        decorations.clear()
    }
}
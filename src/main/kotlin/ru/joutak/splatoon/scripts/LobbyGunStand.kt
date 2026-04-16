package ru.joutak.splatoon.scripts

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.commands.AdminItems
import ru.joutak.splatoon.config.SplatoonSettings

object LobbyGunStand {
    private val displays = mutableMapOf<Location, ItemDisplay>()

    fun spawnAll(locations: List<Location>) {
        removeAll()

        for (loc in locations) {
            val display = loc.world?.spawn(loc, ItemDisplay::class.java)?.apply {
                setItemStack(AdminItems.gun(-1))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                isGlowing = true
                addScoreboardTag("lobby_gun_stand")
            }
            if (display != null) {
                displays[loc] = display
            }
        }

        SplatoonPlugin.instance.logger.info("Spawned ${displays.size} gun stands in lobby")
    }

    fun removeAll() {
        displays.values.forEach { it.remove() }
        displays.clear()

        val lobbyWorld = Bukkit.getWorld(SplatoonSettings.lobbyWorldName)
        if (lobbyWorld != null) {
            lobbyWorld.entities.filterIsInstance<ItemDisplay>()
                .filter { it.scoreboardTags.contains("lobby_gun_stand") }
                .forEach { it.remove() }
        }
    }

    fun tryPickup(player: Player, location: Location): Boolean {
        if (!displays.containsKey(location)) return false

        if (hasGunInInventory(player)) {
            return false
        }

        player.inventory.addItem(AdminItems.gun(-1))
        return true
    }

    private fun hasGunInInventory(player: Player): Boolean {
        return player.inventory.contents.any { item ->
            item != null && item.type == Material.CROSSBOW && item.hasItemMeta() &&
                    item.itemMeta.persistentDataContainer.has(
                        org.bukkit.NamespacedKey(SplatoonPlugin.instance, "splatGun"),
                        org.bukkit.persistence.PersistentDataType.BOOLEAN
                    )
        }
    }

    fun getLocations(): Set<Location> = displays.keys
}
package ru.joutak.splatoon.scripts

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.persistence.PersistentDataType
import ru.joutak.splatoon.SplatoonPlugin

fun giveSplatBomb(world: World) {
    val item = ItemStack(Material.GOLDEN_AXE, 1)
    val meta = item.itemMeta
    val plugin = SplatoonPlugin.instance
    meta.displayName(
        Component.text("Сплат-бомба")
            .color(TextColor.color(0xFF55FF))
    )
    meta.displayName(
        Component.text("Сплат-бомба").color(TextColor.color(0xFF55FF))
    )
    meta.persistentDataContainer.set(NamespacedKey(plugin, "Bomb"), PersistentDataType.BOOLEAN, true)
    item.itemMeta = meta
    val loc = SplatoonPlugin.instance.boostLocations.random()
    Location(world, loc[0], loc[1], loc[2]).world.dropItem(Location(world, loc[0], loc[1], loc[2]), item)

}
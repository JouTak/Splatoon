package ru.joutak.splatoon.scripts

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

fun giveSplatBomb(plugin: Plugin, player: Player, n: Int = 1) {
    val item = ItemStack(Material.GOLDEN_AXE, n)
    val meta = item.itemMeta
    meta.displayName(
        Component.text("Сплат-бомба")
            .color(TextColor.color(0xFF55FF))
    )
    meta.persistentDataContainer.set(NamespacedKey(plugin, "splatBomb"), PersistentDataType.BOOLEAN, true)
    item.itemMeta = meta
    player.inventory.addItem(item)
}
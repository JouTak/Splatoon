package ru.joutak.splatoon.scripts

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import ru.joutak.splatoon.SplatoonPlugin

fun giveSplatBomb() : ItemStack {
    val item = ItemStack(Material.GOLDEN_AXE, 1)
    val meta = item.itemMeta
    val plugin = SplatoonPlugin.instance
    meta.displayName(
        Component.text("Сплат-бомба").color(TextColor.color(0xFF55FF))
    )
    meta.persistentDataContainer.set(NamespacedKey(plugin, "Bomb"), PersistentDataType.BOOLEAN, true)
    item.itemMeta = meta

    return item
}

fun giveBacillus() : ItemStack {
    val item = ItemStack(Material.AMETHYST_SHARD, 1)
    val meta = item.itemMeta
    val plugin = SplatoonPlugin.instance
    meta.displayName(
        Component.text("Бацилла").color(TextColor.color(0xFF55FF))
    )
    meta.persistentDataContainer.set(NamespacedKey(plugin, "Bacillus"), PersistentDataType.BOOLEAN, true)
    item.itemMeta = meta

    return item
}

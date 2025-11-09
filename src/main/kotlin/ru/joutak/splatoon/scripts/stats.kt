package ru.joutak.splatoon.scripts

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.collections.Map
import java.util.UUID
import ru.joutak.splatoon.SplatoonPlugin

object Stats {
    var paintedCommand: MutableMap<Int, Int> = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    var paintedPerson: MutableMap<UUID, Int> = mutableMapOf()
    val commandColors: Map<Int, Material> = mapOf(
        0 to Material.RED_CONCRETE,
        1 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        3 to Material.YELLOW_CONCRETE
    )
    var commands: Map<UUID, Int> = mutableMapOf()
    fun startGame() {
        val item = ItemStack(Material.GOLDEN_SHOVEL, 1)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Сплат-пушка")
                .color(TextColor.color(0xFF55FF))
        )
        meta.persistentDataContainer.set(NamespacedKey(SplatoonPlugin.instance, "splatGun"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        commands.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.inventory?.addItem(item)
        }
    }

}
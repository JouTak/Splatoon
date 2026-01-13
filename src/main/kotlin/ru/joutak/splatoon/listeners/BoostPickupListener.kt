package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.joutak.splatoon.SplatoonPlugin
import java.time.Duration

class BoostPickupListener(private val plugin: Plugin) : Listener {

    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val stack = event.item.itemStack
        if (!stack.hasItemMeta()) return

        val pdc = stack.itemMeta.persistentDataContainer
        val splat = NamespacedKey(plugin, "Bomb")
        val bac = NamespacedKey(plugin, "Bacillus")

        if (pdc.has(splat, PersistentDataType.BOOLEAN)) {
            val t = Title.title(
                Component.text("БУСТ ПОЛУЧЕН!", NamedTextColor.GOLD),
                Component.text("Сплат-бомба", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(150))
            )
            player.showTitle(t)
            return
        }

        if (pdc.has(bac, PersistentDataType.BOOLEAN)) {
            val t = Title.title(
                Component.text("БУСТ ПОЛУЧЕН!", NamedTextColor.LIGHT_PURPLE),
                Component.text("Бацилла", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(150))
            )
            player.showTitle(t)
        }
    }
}

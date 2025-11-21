package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.event.block.Action
import org.bukkit.potion.PotionEffectType

class PlayerUseListener(val plugin: Plugin) : Listener {
    @EventHandler
    fun playerUseItemEvent(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && player.inventory.itemInMainHand.type == Material.GOLDEN_SHOVEL && player.inventory.itemInMainHand.itemMeta.persistentDataContainer.has(
                NamespacedKey(plugin, "splatGun"), PersistentDataType.BOOLEAN
            )
        ) player.world.spawnEntity(
            Location(
                player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z
            ).add(player.location.direction), EntityType.SNOWBALL
        ).apply {
            velocity = player.location.direction.multiply(1.4)
            setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            setMetadata("shooter", FixedMetadataValue(plugin, player.name))
        }
        if (player.inventory.itemInMainHand.type == Material.GOLDEN_AXE && player.inventory.itemInMainHand.itemMeta.persistentDataContainer.has(
                NamespacedKey(plugin, "splatBomb"), PersistentDataType.BOOLEAN
            )
        ) {
            player.world.spawnEntity(
                Location(
                    player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z
                ).add(player.location.direction), EntityType.SNOWBALL
            ).apply {
                customName(Component.text("Bomb"))
                isCustomNameVisible = false
                velocity = player.location.direction.multiply(1.1)
                setMetadata("paintKey", FixedMetadataValue(plugin, 1))
                setMetadata("bombKey", FixedMetadataValue(plugin, 1))
                setMetadata("shooter", FixedMetadataValue(plugin, player.name))
            }
            player.inventory.setItemInMainHand(null)
        }
    }
}
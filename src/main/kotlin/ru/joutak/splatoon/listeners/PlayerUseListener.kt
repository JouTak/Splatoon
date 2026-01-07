package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.scripts.GameManager

class PlayerUseListener(private val plugin: Plugin) : Listener {

    @EventHandler
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type == Material.AIR) return

        val meta = itemInHand.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        val game = GameManager.playerGame[player.uniqueId]

        val commandColors = mapOf(
            0 to "Red",
            3 to "Blue",
            2 to "Green",
            1 to "Yellow"
        )

        if (itemInHand.type == Material.GOLDEN_SHOVEL && pdc.has(
                NamespacedKey(plugin, "splatGun"), PersistentDataType.BOOLEAN
            )
        ) {
            if (game == null) return
            val baseTeam = game.commands[player.uniqueId] ?: return
            val paintTeam = game.getAmmoTeam(player.uniqueId) ?: baseTeam

            val colorName = commandColors[paintTeam] ?: return
            val projectileItem = createProjectileItem(colorName)

            player.world.spawn(
                Location(
                    player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z
                ).add(player.location.direction),
                Snowball::class.java
            ).apply {
                item = projectileItem
                velocity = player.location.direction.multiply(1.4)
                shooter = player

                setMetadata("paintKey", FixedMetadataValue(plugin, 1))
                setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
                setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
            }
            return
        }

        if (itemInHand.type == Material.GOLDEN_AXE && pdc.has(
                NamespacedKey(plugin, "Bomb"), PersistentDataType.BOOLEAN
            )
        ) {
            if (game == null) return
            val baseTeam = game.commands[player.uniqueId] ?: return
            val paintTeam = game.getAmmoTeam(player.uniqueId) ?: baseTeam

            val colorName = commandColors[paintTeam] ?: "Bomb"
            val projectileItem = createProjectileItem(colorName)

            player.world.spawn(
                Location(
                    player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z
                ).add(player.location.direction),
                Snowball::class.java
            ).apply {
                item = projectileItem
                velocity = player.location.direction.multiply(1.1)
                shooter = player

                setMetadata("paintKey", FixedMetadataValue(plugin, 1))
                setMetadata("bombKey", FixedMetadataValue(plugin, 1))
                setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
                setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
            }

            player.inventory.setItemInMainHand(null)
        }
    }

    private fun createProjectileItem(name: String): ItemStack {
        val stack = ItemStack(Material.SNOWBALL, 1)
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name))
        return stack
    }
}

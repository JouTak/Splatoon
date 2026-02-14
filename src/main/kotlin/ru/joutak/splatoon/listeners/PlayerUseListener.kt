package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
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
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class PlayerUseListener(private val plugin: Plugin) : Listener {

    private val ceremonyKey = "ceremonyKey"

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

        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(
            NamespacedKey(plugin, "splatoonAdmin"), PersistentDataType.BOOLEAN
        )

        if (itemInHand.type == Material.GOLDEN_AXE && pdc.has(
                NamespacedKey(plugin, "Bomb"), PersistentDataType.BOOLEAN
            )
        ) {
            if (game == null && !isAdminUse) return

            val baseTeam = if (game != null) {
                game.commands[player.uniqueId] ?: return
            } else {
                pdc.get(NamespacedKey(plugin, "adminTeam"), PersistentDataType.INTEGER) ?: 0
            }
            val paintTeam = if (game != null) {
                game.getAmmoTeam(player.uniqueId) ?: baseTeam
            } else {
                GameManager.getAdminAmmoTeam(player.uniqueId, baseTeam)
            }

            val inCeremony = GameManager.getCeremonyBounds(player.uniqueId)?.worldName == player.world.name

            // Bomb визуально должен оставаться "Bomb" (ресурспак подхватывает модель по имени).
            // Цвет команды берётся из метаданных (paintTeam/baseTeam), а не из имени предмета.
            val projectileItem = createProjectileItem("Bomb")

            val dir = player.eyeLocation.direction.normalize()

            // Throw sound for everyone nearby.
            player.world.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 0.75f, 0.85f)

            player.world.spawn(
                Location(
                    player.world, player.eyeLocation.x, player.eyeLocation.y - 0.1, player.eyeLocation.z
                ).add(dir),
                Snowball::class.java
            ).apply {
                item = projectileItem
                setGravity(true)
                val vel = dir.clone().multiply(SplatoonSettings.bombVelocity * SplatoonSettings.bombHorizontalMultiplier)
                vel.y += SplatoonSettings.bombUpwardBoost
                velocity = vel
                shooter = player

                setMetadata("paintKey", FixedMetadataValue(plugin, 1))
                setMetadata("bombKey", FixedMetadataValue(plugin, 1))
                setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
                setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
                setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))

                if (inCeremony) {
                    setMetadata(ceremonyKey, FixedMetadataValue(plugin, 1))
                }
            }

            if (!isAdminUse && !inCeremony) {
                val item = player.inventory.itemInMainHand
                if (item.type != Material.AIR) {
                    if (item.amount <= 1) {
                        player.inventory.setItemInMainHand(null)
                    } else {
                        item.amount = item.amount - 1
                        player.inventory.setItemInMainHand(item)
                    }
                }
            }
        }
    }

    private fun createProjectileItem(name: String): ItemStack {
        val stack = ItemStack(Material.SNOWBALL, 1)
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name))
        return stack
    }
}

package ru.joutak.splatoon.listeners

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.LingeringPotionSplashEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class BacillusThrowListener(private val plugin: Plugin) : Listener {

    private val bacillusKey = NamespacedKey(plugin, "Bacillus")

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = player.inventory.itemInMainHand

        if (item.type != Material.LINGERING_POTION) return
        if (!item.hasItemMeta()) return

        val pdc = item.itemMeta.persistentDataContainer
        if (!pdc.has(bacillusKey, PersistentDataType.BOOLEAN)) return

        val game = GameManager.playerGame[player.uniqueId] ?: return

        event.isCancelled = true

        val attackerTeam = game.commands[player.uniqueId] ?: return

        val throwable = player.launchProjectile(ThrownPotion::class.java)
        throwable.shooter = player
        throwable.setMetadata("bacillusThrow", FixedMetadataValue(plugin, true))
        throwable.setMetadata("bacillusTeam", FixedMetadataValue(plugin, attackerTeam))

        val potionItem = ItemStack(Material.LINGERING_POTION, 1)
        val potionMeta = potionItem.itemMeta as PotionMeta
        potionMeta.setColor(getTeamColor(attackerTeam))
        potionMeta.displayName(item.itemMeta.displayName())
        potionItem.itemMeta = potionMeta
        throwable.item = potionItem

        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_LINGERING_POTION_THROW, 1.0f, 1.0f)

        if (item.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            item.amount = item.amount - 1
            player.inventory.setItemInMainHand(item)
        }

        plugin.logger.info("[Bacillus] Player ${player.name} threw bacillus potion (team $attackerTeam)")
    }

    @EventHandler
    fun onPotionHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile !is ThrownPotion) return

        if (!projectile.hasMetadata("bacillusThrow")) return

        val team = projectile.getMetadata("bacillusTeam").firstOrNull()?.asInt() ?: return
        val hitLocation = projectile.location

        plugin.logger.info("[Bacillus] Potion hit ground at ${hitLocation.x}, ${hitLocation.y}, ${hitLocation.z} for team $team")

        projectile.remove()

        val cloud = hitLocation.world.spawn(hitLocation, AreaEffectCloud::class.java)
        cloud.setDuration(SplatoonSettings.bacillusCloudDurationTicks)
        cloud.setRadius(SplatoonSettings.bacillusCloudRadius)
        cloud.setRadiusPerTick(0.0f)
        cloud.setColor(getTeamColor(team))
        cloud.setWaitTime(0)
        cloud.clearCustomEffects()
        cloud.setMetadata("bacillusCloud", FixedMetadataValue(plugin, true))
        cloud.setMetadata("bacillusCloudTeam", FixedMetadataValue(plugin, team))

        plugin.logger.info("[Bacillus] Cloud created! Radius=${cloud.radius}, Duration=${cloud.duration}, Color=${cloud.color}")
    }

    private fun getTeamColor(team: Int): Color {
        return when (team) {
            0 -> Color.RED
            1 -> Color.YELLOW
            2 -> Color.GREEN
            3 -> Color.BLUE
            else -> Color.PURPLE
        }
    }
}
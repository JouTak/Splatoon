package ru.joutak.splatoon.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class DamageGuardListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val game = GameManager.playerGame[player.uniqueId] ?: return
        if (player.world.name != game.worldName) return

        event.isCancelled = true

        if (event.cause == EntityDamageEvent.DamageCause.VOID) {
            game.resetInkHp(player.uniqueId)
            player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }
            game.teleportToSpawn(player)
            game.setSpawnProtection(player, SplatoonSettings.spawnProtectionAfterRespawnSeconds * 1000L)
            game.syncHealthBar(player)
            player.fireTicks = 0
            player.noDamageTicks = SplatoonSettings.spawnProtectionNoDamageTicks
            player.foodLevel = 20
            player.saturation = 20f
        }
    }
}

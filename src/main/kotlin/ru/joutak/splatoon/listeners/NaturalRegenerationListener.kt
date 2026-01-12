package ru.joutak.splatoon.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import ru.joutak.splatoon.scripts.GameManager

class NaturalRegenerationListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRegainHealth(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val game = GameManager.playerGame[player.uniqueId] ?: return
        if (player.world.name != game.worldName) return

        // We use custom Ink HP. Vanilla regeneration (peaceful/food/potions/etc.) breaks the visual HP bar.
        event.isCancelled = true
        game.syncHealthBar(player)
    }
}

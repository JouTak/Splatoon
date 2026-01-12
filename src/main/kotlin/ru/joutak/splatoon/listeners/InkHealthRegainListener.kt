package ru.joutak.splatoon.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import ru.joutak.splatoon.scripts.GameManager

class InkHealthRegainListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRegain(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val game = GameManager.playerGame[player.uniqueId] ?: return

        // Ink HP drives the health bar during the match. Cancel vanilla heals (peaceful, food, potions, etc.)
        if (event.regainReason != EntityRegainHealthEvent.RegainReason.CUSTOM) {
            event.isCancelled = true
            game.syncHealthBar(player)
        }
    }
}

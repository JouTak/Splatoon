package ru.joutak.splatoon.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import ru.joutak.splatoon.scripts.GameManager

class SpawnProtectionMoveListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        val from = event.from

        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        val player = event.player
        val game = GameManager.playerGame[player.uniqueId] ?: return
        if (player.world.name != game.worldName) return

        game.onPlayerMoved(player)
    }
}

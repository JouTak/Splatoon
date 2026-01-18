package ru.joutak.splatoon.listeners

import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import ru.joutak.splatoon.scripts.GameManager

class CeremonyMoveListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val bounds = GameManager.getCeremonyBounds(player.uniqueId) ?: return

        if (player.world.name != bounds.worldName) {
            // Player left the ceremony world by teleport/command.
            GameManager.clearCeremonyBounds(player.uniqueId)
            return
        }

        val to = event.to ?: return
        val x = to.x
        val z = to.z
        if (x >= bounds.minX && x < bounds.maxX && z >= bounds.minZ && z < bounds.maxZ) return

        val safe = bounds.safeLocation
        val clampedX = x.coerceIn(bounds.minX + 0.001, bounds.maxX - 0.001)
        val clampedZ = z.coerceIn(bounds.minZ + 0.001, bounds.maxZ - 0.001)

        val newTo = Location(player.world, clampedX, to.y, clampedZ, to.yaw, to.pitch)
        // If the player is somehow falling far below the podium, snap back onto it.
        if (newTo.y < safe.y - 6.0) newTo.y = safe.y

        event.to = newTo
    }
}

package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerTeleportEvent
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID

/**
 * Same as [SpectatorTeleportGuardListener], but kept under this name because SplatoonPlugin registers it.
 * Prevents Splatoon spectators from using vanilla SPECTATOR hotbar teleport to leave the match world.
 */
class SpectatorWorldTeleportGuardListener : Listener {

    private val lastWarnAtMs: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpectatorTeleport(event: PlayerTeleportEvent) {
        if (event.cause != PlayerTeleportEvent.TeleportCause.SPECTATE) return

        val player = event.player
        if (player.gameMode != GameMode.SPECTATOR) return

        // Admin /sp spectate uses this mapping.
        val spectatingGame = GameManager.getSpectatingGame(player)
        // Extra safety: if someone ends up as SPECTATOR inside a running game.
        val inGame = spectatingGame ?: GameManager.getGame(player)
        if (inGame == null) return

        val toWorld = event.to?.world ?: return
        val expectedWorldName = inGame.worldName
        if (toWorld.name == expectedWorldName) return

        event.isCancelled = true
        warnOncePerSecond(player.uniqueId)

        // If client-side already switched target, force a safe position back in the match world.
        Bukkit.getScheduler().runTask(SplatoonPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            if (player.gameMode != GameMode.SPECTATOR) return@Runnable

            val w = Bukkit.getWorld(expectedWorldName) ?: return@Runnable
            player.teleport(w.spawnLocation)
            player.gameMode = GameMode.SPECTATOR
            player.isCollidable = false
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (player.gameMode != GameMode.SPECTATOR) return

        val spectatingGame = GameManager.getSpectatingGame(player) ?: return
        val expectedWorldName = spectatingGame.worldName
        if (player.world.name == expectedWorldName) return

        warnOncePerSecond(player.uniqueId)

        Bukkit.getScheduler().runTask(SplatoonPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            if (player.gameMode != GameMode.SPECTATOR) return@Runnable
            val w = Bukkit.getWorld(expectedWorldName) ?: return@Runnable
            player.teleport(w.spawnLocation)
            player.gameMode = GameMode.SPECTATOR
            player.isCollidable = false
        })
    }

    private fun warnOncePerSecond(uuid: UUID) {
        val now = System.currentTimeMillis()
        val last = lastWarnAtMs[uuid] ?: 0L
        if (now - last < 1000L) return
        lastWarnAtMs[uuid] = now
        Bukkit.getPlayer(uuid)?.sendMessage(Component.text("§cВ режиме наблюдения нельзя телепортироваться в другие миры."))
    }
}

package ru.joutak.splatoon.listeners

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.GameManager

class PlayerMoveOnIceListener : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId

        if (GameManager.playerGame[uuid] == null) return

        if (!SplatoonSettings.speedupOnIceEnabled) return

        val loc = player.location

        if (isOnIce(loc)) {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SPEED,
                    -1,
                    SplatoonSettings.speedupOnIceAmplifier,
                    false,
                    false,
                    true
                )
            )
        } else {
            val effect = player.getPotionEffect(PotionEffectType.SPEED)?: return

            if (effect.duration == -1) {
                player.removePotionEffect(PotionEffectType.SPEED)
            }
        }
    }

    private fun isOnIce(loc: Location): Boolean {
        var onIce = false

        for (dx in -1..1) {
            for (dz in -1..1) {
                val check = loc.clone().add(dx.toDouble() * 0.1, -1.0, dz.toDouble() * 0.1)
                if (check.block.type == Material.BLUE_ICE) {
                    onIce = true
                    break
                }
            }
            if (onIce) break
        }

        return onIce
    }
}

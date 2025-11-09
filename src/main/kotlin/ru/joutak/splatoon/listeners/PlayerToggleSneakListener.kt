package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.SplatoonPlugin
import java.util.UUID

class PlayerToggleSneakListener : Listener {
    companion object {
        val tasks = mutableMapOf<UUID, Int>()
    }

    @EventHandler
    fun playerToggleSneakEvent(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (tasks.containsKey(player.uniqueId)) {
            Bukkit.getScheduler().cancelTask(tasks[player.uniqueId]!!)
            tasks.remove(player.uniqueId)
            return
        }

        tasks[player.uniqueId] =
            Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
                for (x in -3..3) {
                    for (z in -3..3) {
                        if (Location(
                                player.world,
                                player.location.x + x.toDouble() / 10,
                                player.location.y,
                                player.location.z + z.toDouble() / 10
                            ).block.getRelative(
                                BlockFace.DOWN
                            ).type == Material.GREEN_CONCRETE
                        ) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 2, 18))
                            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 2, 1))
                        }
                    }
                }
            }, 0L, 1).taskId
    }
}


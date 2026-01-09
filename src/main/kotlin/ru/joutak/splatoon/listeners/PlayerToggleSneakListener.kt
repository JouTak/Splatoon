package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID

class PlayerToggleSneakListener : Listener {
    companion object {
        val tasks: MutableMap<UUID, Int> = mutableMapOf()
    }

    @EventHandler
    fun onSneakToggle(event: PlayerToggleSneakEvent) {
        val player = event.player
        val uuid = player.uniqueId

        val settings = SplatoonPlugin.instance.settings
        val move = settings.movement.sneakOnInk
        if (!move.enabled) {
            val existing = tasks.remove(uuid)
            if (existing != null) Bukkit.getScheduler().cancelTask(existing)
            return
        }

        if (tasks.containsKey(uuid)) {
            val taskId = tasks.remove(uuid)
            if (taskId != null) Bukkit.getScheduler().cancelTask(taskId)
            return
        }

        val task = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            val game = GameManager.playerGame[uuid] ?: run {
                val existing = tasks.remove(uuid)
                if (existing != null) Bukkit.getScheduler().cancelTask(existing)
                return@Runnable
            }

            val team = game.commands[uuid] ?: return@Runnable
            val teamMaterial = game.commandColors[team] ?: return@Runnable

            val loc = player.location
            val step = move.scanStepBlocks
            val steps = move.scanSteps

            var onInk = false
            for (dx in -steps..steps) {
                for (dz in -steps..steps) {
                    val check = loc.clone().add(dx * step, -1.0, dz * step)
                    if (check.block.type == teamMaterial) {
                        onInk = true
                        break
                    }
                }
                if (onInk) break
            }

            if (onInk) {
                player.addPotionEffect(
                    PotionEffect(PotionEffectType.SPEED, move.effectDurationTicks, move.speedAmplifier, false, false, true)
                )
                if (move.invisibilityAmplifier >= 0) {
                    player.addPotionEffect(
                        PotionEffect(PotionEffectType.INVISIBILITY, move.effectDurationTicks, move.invisibilityAmplifier, false, false, true)
                    )
                }
            }
        }, 0L, move.taskPeriodTicks)

        tasks[uuid] = task.taskId
    }
}

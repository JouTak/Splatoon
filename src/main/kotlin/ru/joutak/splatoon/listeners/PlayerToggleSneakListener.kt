package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.config.SplatoonSettings
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

        val gameNow = GameManager.playerGame[uuid]
        if (gameNow == null) {
            val existing = tasks.remove(uuid)
            if (existing != null) Bukkit.getScheduler().cancelTask(existing)
            return
        }

        if (!SplatoonSettings.sneakOnInkEnabled) {
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
            val step = SplatoonSettings.sneakOnInkScanStepBlocks
            val steps = SplatoonSettings.sneakOnInkScanSteps

            var onInk = false
            for (dx in -steps..steps) {
                for (dz in -steps..steps) {
                    val check = loc.clone().add(dx.toDouble() * step, -1.0, dz.toDouble() * step)
                    if (check.block.type == teamMaterial) {
                        onInk = true
                        break
                    }
                }
                if (onInk) break
            }

            if (onInk) {
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.SPEED,
                        SplatoonSettings.sneakOnInkEffectDurationTicks,
                        SplatoonSettings.sneakOnInkSpeedAmplifier,
                        false,
                        false,
                        true
                    )
                )

                if (SplatoonSettings.sneakOnInkInvisibilityAmplifier >= 0) {
                    player.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.INVISIBILITY,
                            SplatoonSettings.sneakOnInkEffectDurationTicks,
                            SplatoonSettings.sneakOnInkInvisibilityAmplifier,
                            false,
                            false,
                            true
                        )
                    )
                }
            }
        }, 0L, SplatoonSettings.sneakOnInkTaskPeriodTicks)

        tasks[uuid] = task.taskId
    }
}

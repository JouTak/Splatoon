package ru.joutak.splatoon.scripts

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.config.SplatoonSettings
import java.time.Duration
import java.util.*

class BacillusCloudTask : BukkitRunnable() {

    private val recentlyInfected = mutableMapOf<UUID, Long>()
    private val effectTasks = mutableMapOf<UUID, BukkitRunnable>()

    override fun run() {
        val worlds = Bukkit.getWorlds()
        val now = System.currentTimeMillis()
        var totalClouds = 0

        for (world in worlds) {
            val clouds = world.entities.filterIsInstance<AreaEffectCloud>()
                .filter { it.hasMetadata("bacillusCloud") }

            totalClouds += clouds.size

            for (cloud in clouds) {
                val team = cloud.getMetadata("bacillusCloudTeam").firstOrNull()?.asInt() ?: continue
                val radius = cloud.radius.toDouble()

                val playersInCloud = cloud.getNearbyEntities(radius, radius, radius)
                    .filterIsInstance<Player>().filter { player ->
                        player.location.distance(cloud.location) <= radius + 0.5
                    }

                for (player in playersInCloud) {
                    val game = GameManager.playerGame[player.uniqueId] ?: continue
                    val victimTeam = game.commands[player.uniqueId] ?: continue

                    if (team == victimTeam) continue
                    if (game.isSpawnSafe(player)) continue

                    if (recentlyInfected.containsKey(player.uniqueId)) {
                        recentlyInfected[player.uniqueId] = now

                        continue
                    }

                    recentlyInfected[player.uniqueId] = now


                    player.world.playSound(player.location, org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f)

                    val title = Title.title(
                        Component.text("☣ ЗАРАЖЕНИЕ!", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Теперь вы красите пол цветом врага!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(150))
                    )
                    player.showTitle(title)

                    startEffect(player, team, SplatoonSettings.bacillusDurationSeconds)

                    SplatoonPlugin.instance.logger.info("[Bacillus] Player ${player.name} infected by team $team")
                }
            }
        }

        val iterator = recentlyInfected.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > SplatoonSettings.bacillusDurationSeconds * 1000L) {
                stopEffect(entry.key)
                iterator.remove()
            }
        }
    }

    fun start() {
        this.runTaskTimer(SplatoonPlugin.instance, 0L, SplatoonSettings.bacillusCloudCheckPeriodTicks)
        SplatoonPlugin.instance.logger.info("[Bacillus] Cloud task started")
    }

    fun stop() {
        this.cancel()
        effectTasks.values.forEach { it.cancel() }
        effectTasks.clear()
        SplatoonPlugin.instance.logger.info("[Bacillus] Cloud task stopped")
    }

    private fun startEffect(player: Player, team: Int, durationSeconds: Int){
        val color = getTeamColor(team)

        val task = object : BukkitRunnable() {
            private var ticks = 0
            private val maxTicks = durationSeconds * 20

            override fun run() {
                if (ticks >= maxTicks || !player.isOnline || player.isDead) {
                    stopEffect(player.uniqueId)
                    this.cancel()
                    return
                }

                val location = player.location
                val world = player.world

                if (SplatoonSettings.bacillusGlowEnabled) {
                    for (i in 0 until 8) {
                        val angle = (ticks * 0.5 + i * 45.0) * Math.PI / 180.0
                        val radius = 0.8
                        val xOffset = Math.cos(angle) * radius
                        val zOffset = Math.sin(angle) * radius
                        val yOffset = 0.5 + Math.sin(ticks * 0.3) * 0.3

                        world.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            location.clone().add(xOffset, yOffset, zOffset),
                            1,
                            0.0, 0.0, 0.0, 0.0,
                            Particle.DustTransition(color, color, 0.8f)
                        )
                    }
                }

                val locationUnder = location.clone().subtract(0.0, 1.0, 0.0).block.location.clone()

                SplatoonPlugin.projectileHitListener.safePaintInRadius(locationUnder, world, 0.7, team)

//                world.spawnParticle(
//                    Particle.DUST_COLOR_TRANSITION,
//                    location.clone().add(0.0, 1.2, 0.0),
//                    2,
//                    0.2, 0.3, 0.2, 0.0,
//                    Particle.DustTransition(color, color, 0.6f)
//                )

                ticks++
            }
        }

        effectTasks[player.uniqueId] = task
        task.runTaskTimer(SplatoonPlugin.instance, 0L, 2L)
    }

    private fun stopEffect(playerId: UUID) {
        effectTasks[playerId]?.cancel()
        effectTasks.remove(playerId)
    }

    private fun getTeamColor(team: Int): Color {
        return when (team) {
            0 -> Color.RED
            1 -> Color.YELLOW
            2 -> Color.GREEN
            3 -> Color.BLUE
            else -> Color.PURPLE
        }
    }
}
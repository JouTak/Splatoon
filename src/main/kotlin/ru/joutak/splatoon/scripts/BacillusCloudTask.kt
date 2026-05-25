package ru.joutak.splatoon.scripts

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.config.SplatoonSettings
import java.time.Duration
import java.util.*

class BacillusCloudTask : BukkitRunnable() {

    private val recentlyInfected = mutableMapOf<UUID, Long>()

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

                    val lastInfected = recentlyInfected[player.uniqueId] ?: 0L
                    if (now - lastInfected < SplatoonSettings.bacillusInfectionCooldownMs) continue

                    recentlyInfected[player.uniqueId] = now
                    game.setTemporaryTeam(player.uniqueId, team, SplatoonSettings.bacillusDurationSeconds * 1000L)

                    player.world.playSound(player.location, org.bukkit.Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f)

                    val title = Title.title(
                        Component.text("☣ ЗАРАЖЕНИЕ!", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Теперь вы красите цветом врага!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(150))
                    )
                    player.showTitle(title)

                    if (SplatoonSettings.bacillusGlowEnabled) {
                        player.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.GLOWING,
                                SplatoonSettings.bacillusDurationSeconds * 20,
                                0,
                                false,
                                false,
                                true
                            )
                        )
                    }

                    SplatoonPlugin.instance.logger.info("[Bacillus] Player ${player.name} infected by team $team")
                }
            }
        }

        val iterator = recentlyInfected.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > SplatoonSettings.bacillusDurationSeconds * 1000L) {
                iterator.remove()
            }
        }
    }

    fun start() {
        this.runTaskTimer(SplatoonPlugin.instance, 0L, SplatoonSettings.bacillusCloudCheckPeriodTicks)
        SplatoonPlugin.instance.logger.info("[Bacillus] Cloud task started")
    }
}
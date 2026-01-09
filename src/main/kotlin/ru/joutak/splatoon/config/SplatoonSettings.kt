package ru.joutak.splatoon.config

import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.min

data class SplatoonSettings(
    val lobbyWorld: String,
    val game: GameSettings,
    val ink: InkSettings,
    val spawnProtection: SpawnProtectionSettings,
    val weapons: WeaponSettings,
    val bacillus: BacillusSettings,
    val boosts: BoostSettings,
    val movement: MovementSettings
) {
    data class GameSettings(
        val durationSeconds: Int,
        val returnToLobbyDelaySeconds: Int,
        val scoreboardUpdateTicks: Long,
        val actionBarUpdateTicks: Long
    )

    data class InkSettings(
        val maxHp: Int
    )

    data class SpawnProtectionSettings(
        val radiusBlocks: Double,
        val afterRespawnSeconds: Int,
        val resistanceDurationTicks: Int,
        val resistanceAmplifier: Int,
        val noDamageTicks: Int
    )

    data class WeaponSettings(
        val gun: GunSettings,
        val bomb: BombSettings
    )

    data class GunSettings(
        val velocity: Double,
        val paintRadius: Double,
        val killPaintRadius: Double
    )

    data class BombSettings(
        val velocity: Double,
        val paintRadius: Double,
        val killPaintRadius: Double
    )

    data class BacillusSettings(
        val durationSeconds: Int,
        val rangeBlocks: Double,
        val cooldownMs: Long
    )

    data class BoostSettings(
        val enabled: Boolean,
        val initialDelaySeconds: Int,
        val minIntervalSeconds: Int,
        val maxIntervalSeconds: Int,
        val bombPercent: Int,
        val locations: List<List<Double>>
    ) {
        fun nextIntervalSeconds(): Int {
            val lo = min(minIntervalSeconds, maxIntervalSeconds)
            val hi = max(minIntervalSeconds, maxIntervalSeconds)
            if (hi <= lo) return lo
            return (lo..hi).random()
        }
    }

    data class MovementSettings(
        val sneakOnInk: SneakOnInkSettings
    )

    data class SneakOnInkSettings(
        val enabled: Boolean,
        val scanSteps: Int,
        val scanStepBlocks: Double,
        val speedAmplifier: Int,
        val invisibilityAmplifier: Int,
        val effectDurationTicks: Int,
        val taskPeriodTicks: Long
    )

    companion object {
        fun load(config: FileConfiguration, logger: Logger): SplatoonSettings {
            val lobbyWorld = config.getString("lobby.world")
                ?: config.getString("lobby_name")
                ?: "world"

            val durationSeconds = intClamped(
                config.getInt("game.duration_seconds", config.getInt("game_duration_seconds", 300)),
                30,
                60 * 60
            )
            val returnDelaySeconds = intClamped(
                config.getInt("game.return_to_lobby_delay_seconds", 5),
                0,
                60
            )

            val scoreboardUpdateTicks = longClamped(config.getLong("ui.scoreboard_update_ticks", 10L), 1L, 200L)
            val actionBarUpdateTicks = longClamped(config.getLong("ui.actionbar_update_ticks", 5L), 1L, 200L)

            val maxHp = intClamped(config.getInt("ink.max_hp", 3), 1, 20)

            val spawnRadius = doubleClamped(config.getDouble("spawn_protection.radius_blocks", 7.0), 0.0, 64.0)
            val afterRespawnSeconds = intClamped(config.getInt("spawn_protection.after_respawn_seconds", 4), 0, 60)
            val resistanceDurationTicks = intClamped(config.getInt("spawn_protection.resistance.duration_ticks", 60), 0, 20 * 30)
            val resistanceAmplifier = intClamped(config.getInt("spawn_protection.resistance.amplifier", 10), 0, 255)
            val noDamageTicks = intClamped(config.getInt("spawn_protection.no_damage_ticks", 60), 0, 20 * 30)

            val gunVelocity = doubleClamped(config.getDouble("weapons.gun.velocity", 1.4), 0.1, 10.0)
            val gunPaintRadius = doubleClamped(config.getDouble("weapons.gun.paint_radius", 1.5), 0.1, 16.0)
            val gunKillPaintRadius = doubleClamped(config.getDouble("weapons.gun.kill_paint_radius", 3.0), 0.1, 32.0)

            val bombVelocity = doubleClamped(config.getDouble("weapons.bomb.velocity", 1.1), 0.1, 10.0)
            val bombPaintRadius = doubleClamped(config.getDouble("weapons.bomb.paint_radius", 5.0), 0.1, 32.0)
            val bombKillPaintRadius = doubleClamped(config.getDouble("weapons.bomb.kill_paint_radius", 5.0), 0.1, 32.0)

            val bacillusDurationSeconds = intClamped(config.getInt("bacillus.duration_seconds", 5), 1, 60)
            val bacillusRangeBlocks = doubleClamped(config.getDouble("bacillus.range_blocks", 3.2), 0.5, 10.0)
            val bacillusCooldownMs = longClamped(config.getLong("bacillus.cooldown_ms", 250L), 0L, 10_000L)

            val boostsEnabled = config.getBoolean("boosts.enabled", true)
            val boostsInitialDelaySeconds = intClamped(config.getInt("boosts.spawn.initial_delay_seconds", 0), 0, 300)
            val boostsMinIntervalSeconds = intClamped(config.getInt("boosts.spawn.min_interval_seconds", 18), 1, 3600)
            val boostsMaxIntervalSeconds = intClamped(config.getInt("boosts.spawn.max_interval_seconds", 39), 1, 3600)
            val bombPercent = intClamped(config.getInt("boosts.chances.bomb_percent", 70), 0, 100)

            val globalBoostLocations = readCoordinateTriples(
                config.get("boosts.locations") ?: config.get("boost_locations"),
                logger
            )

            val scanSteps = intClamped(config.getInt("movement.sneak_on_ink.scan_steps", 3), 1, 20)
            val scanStepBlocks = doubleClamped(config.getDouble("movement.sneak_on_ink.scan_step_blocks", 0.1), 0.01, 2.0)
            val speedAmplifier = intClamped(config.getInt("movement.sneak_on_ink.speed_amplifier", 18), 0, 255)
            val invisAmplifier = intClamped(config.getInt("movement.sneak_on_ink.invisibility_amplifier", 1), 0, 255)
            val effectDurationTicks = intClamped(config.getInt("movement.sneak_on_ink.effect_duration_ticks", 2), 1, 200)
            val taskPeriodTicks = longClamped(config.getLong("movement.sneak_on_ink.task_period_ticks", 1L), 1L, 200L)
            val sneakEnabled = config.getBoolean("movement.sneak_on_ink.enabled", true)

            if (boostsEnabled && globalBoostLocations.isEmpty()) {
                logger.warning("boosts.enabled=true, но boosts.locations пустой. Бусты будут отключены для всех арен без своих локаций.")
            }

            return SplatoonSettings(
                lobbyWorld = lobbyWorld,
                game = GameSettings(
                    durationSeconds = durationSeconds,
                    returnToLobbyDelaySeconds = returnDelaySeconds,
                    scoreboardUpdateTicks = scoreboardUpdateTicks,
                    actionBarUpdateTicks = actionBarUpdateTicks
                ),
                ink = InkSettings(maxHp = maxHp),
                spawnProtection = SpawnProtectionSettings(
                    radiusBlocks = spawnRadius,
                    afterRespawnSeconds = afterRespawnSeconds,
                    resistanceDurationTicks = resistanceDurationTicks,
                    resistanceAmplifier = resistanceAmplifier,
                    noDamageTicks = noDamageTicks
                ),
                weapons = WeaponSettings(
                    gun = GunSettings(
                        velocity = gunVelocity,
                        paintRadius = gunPaintRadius,
                        killPaintRadius = gunKillPaintRadius
                    ),
                    bomb = BombSettings(
                        velocity = bombVelocity,
                        paintRadius = bombPaintRadius,
                        killPaintRadius = bombKillPaintRadius
                    )
                ),
                bacillus = BacillusSettings(
                    durationSeconds = bacillusDurationSeconds,
                    rangeBlocks = bacillusRangeBlocks,
                    cooldownMs = bacillusCooldownMs
                ),
                boosts = BoostSettings(
                    enabled = boostsEnabled,
                    initialDelaySeconds = boostsInitialDelaySeconds,
                    minIntervalSeconds = boostsMinIntervalSeconds,
                    maxIntervalSeconds = boostsMaxIntervalSeconds,
                    bombPercent = bombPercent,
                    locations = globalBoostLocations
                ),
                movement = MovementSettings(
                    sneakOnInk = SneakOnInkSettings(
                        enabled = sneakEnabled,
                        scanSteps = scanSteps,
                        scanStepBlocks = scanStepBlocks,
                        speedAmplifier = speedAmplifier,
                        invisibilityAmplifier = invisAmplifier,
                        effectDurationTicks = effectDurationTicks,
                        taskPeriodTicks = taskPeriodTicks
                    )
                )
            )
        }

        fun readCoordinateTriples(raw: Any?, logger: Logger): List<List<Double>> {
            if (raw !is List<*>) return emptyList()
            val out = mutableListOf<List<Double>>()
            raw.forEach { item ->
                if (item !is List<*>) return@forEach
                if (item.size != 3) return@forEach
                val x = (item[0] as? Number)?.toDouble()
                val y = (item[1] as? Number)?.toDouble()
                val z = (item[2] as? Number)?.toDouble()
                if (x == null || y == null || z == null) {
                    logger.warning("Неверная координата буста: $item")
                    return@forEach
                }
                out.add(listOf(x, y, z))
            }
            return out
        }

        fun readCoordinateTriplesFromArena(arena: Map<String, Any>, logger: Logger): List<List<Double>> {
            val boosts = arena["boosts"]
            if (boosts is Map<*, *>) {
                val locations = boosts["locations"]
                return readCoordinateTriples(locations, logger)
            }
            val direct = arena["boost_locations"]
            return readCoordinateTriples(direct, logger)
        }

        private fun intClamped(value: Int, minValue: Int, maxValue: Int): Int {
            return max(minValue, min(maxValue, value))
        }

        private fun longClamped(value: Long, minValue: Long, maxValue: Long): Long {
            return max(minValue, min(maxValue, value))
        }

        private fun doubleClamped(value: Double, minValue: Double, maxValue: Double): Double {
            return max(minValue, min(maxValue, value))
        }
    }
}

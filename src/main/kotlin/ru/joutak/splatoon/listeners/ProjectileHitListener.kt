package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

class ProjectileHitListener : Listener {

    private val ceremonyKey = "ceremonyKey"

    private val lastShooterHitMs = mutableMapOf<UUID, Long>()

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        if (entity.type != EntityType.SNOWBALL) return

        // В церемонии разрешаем просто "пострелять" без покраски и без урона.
        if (entity.hasMetadata(ceremonyKey)) {
            entity.remove()
            return
        }
        if (!entity.hasMetadata("paintKey")) return

        val shooterUuid = getShooterUuid(entity.getMetadata("shooterId").firstOrNull()?.asString())
        val shooter = if (shooterUuid != null) Bukkit.getPlayer(shooterUuid) else null
        if (shooter == null) return

        val game = GameManager.playerGame[shooter.uniqueId] ?: return
        val shooterTeam = game.commands[shooter.uniqueId] ?: return

        val paintTeam = entity.getMetadata("paintTeam").firstOrNull()?.asInt() ?: shooterTeam
        val isBomb = entity.hasMetadata("bombKey")
        val radius = if (isBomb) SplatoonSettings.bombPaintRadius else SplatoonSettings.gunPaintRadius
        // On kill we do an extra burst. For bombs it should feel like the player "exploded" into paint.
        val killPaintRadius = if (isBomb) radius + 1.5 else SplatoonSettings.gunKillPaintRadius
        val damagePerHit = if (isBomb) SplatoonSettings.inkMaxHp else 1

        val hitEntity = event.hitEntity
        val hitBlock = event.hitBlock
        val hitFace = event.hitBlockFace

        if (hitEntity is Player) {
            val victim = hitEntity
            val victimGame = GameManager.playerGame[victim.uniqueId]
            if (victimGame == null || victimGame != game) {
                explosivePaint(radius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, null)
                return
            }

            val victimTeam = game.commands[victim.uniqueId]
            if (victimTeam == null) {
                explosivePaint(radius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, null)
                return
            }

            // Impact sound for nearby players.
            if (isBomb) {
                victim.world.playSound(victim.location, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 1.0f)
                victim.world.playSound(victim.location, Sound.ENTITY_SLIME_SQUISH_SMALL, 1.0f, 0.8f)
            } else {
                victim.world.playSound(victim.location, Sound.ENTITY_SLIME_SQUISH_SMALL, 0.7f, 1.55f)
            }

            val victimProtectedByInk = victim.hasPotionEffect(PotionEffectType.INVISIBILITY)
            val spawnSafe = game.isSpawnSafe(victim)

            val excludeUnder = if (victimProtectedByInk && paintTeam != victimTeam) victim.location.block else null
            explosivePaint(radius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, excludeUnder)

            if (victimProtectedByInk || spawnSafe) return
            if (paintTeam == victimTeam) return
            if (shooterTeam == victimTeam) return

            val hpLeft = game.damageInkHp(victim.uniqueId, damagePerHit)

            // Hit markers (sound + tiny actionbar) for both sides.
            playHitMarker(shooter, victim, hpLeft, game)

            if (hpLeft <= 0) {
                game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                val deathLoc = victim.location.clone()
                splatAndRespawn(victim, game)
                explosivePaint(killPaintRadius, deathLoc, entity.world, game, shooter.uniqueId, paintTeam, null)
            }
            return
        }

        if (hitBlock == null || hitFace == null) return

        val center = hitBlock.getRelative(hitFace).location

        if (!isBomb) {
            // Splat on blocks should be audible to anyone nearby.
            entity.world.playSound(center, Sound.ENTITY_SLIME_SQUISH_SMALL, 0.7f, 1.55f)
            explosivePaint(radius, center, entity.world, game, shooter.uniqueId, paintTeam, null)
            return
        }

        // Bomb explosion sound for everyone nearby.
        entity.world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.1f, 1.0f)
        entity.world.playSound(center, Sound.ENTITY_SLIME_SQUISH_SMALL, 1.2f, 0.75f)

        val victims = entity.world.getNearbyEntities(center, radius, radius, radius)
            .filterIsInstance<Player>()
            .filter { GameManager.playerGame[it.uniqueId] == game }

        val protectedSnapshot = mutableMapOf<UUID, Boolean>()
        victims.forEach { v ->
            protectedSnapshot[v.uniqueId] = v.hasPotionEffect(PotionEffectType.INVISIBILITY) || game.isSpawnSafe(v)
        }

        explosivePaint(radius, center, entity.world, game, shooter.uniqueId, paintTeam, null)

        victims.forEach { victim ->
            val victimTeam = game.commands[victim.uniqueId] ?: return@forEach
            val protected = protectedSnapshot[victim.uniqueId] == true
            if (protected) return@forEach
            if (paintTeam == victimTeam) return@forEach
            if (shooterTeam == victimTeam) return@forEach

            val hpLeft = game.damageInkHp(victim.uniqueId, damagePerHit)

            // Bomb AOE: mark hits too (throttled for shooter).
            playHitMarker(shooter, victim, hpLeft, game)

            if (hpLeft <= 0) {
                game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                val deathLoc = victim.location.clone()
                splatAndRespawn(victim, game)
                explosivePaint(killPaintRadius, deathLoc, entity.world, game, shooter.uniqueId, paintTeam, null)
            }
        }
    }

    private fun playHitMarker(shooter: Player, victim: Player, victimHpLeft: Int, game: Game) {
        // Shooter: short "hitmarker" sound. Throttle so bombs don't spam the ear.
        val now = System.currentTimeMillis()
        val last = lastShooterHitMs[shooter.uniqueId] ?: 0L
        if (now - last >= 120L) {
            shooter.playSound(shooter.location, Sound.ENTITY_ARROW_HIT_PLAYER, 0.7f, 1.6f)
            lastShooterHitMs[shooter.uniqueId] = now

            game.pushActionBarOverlay(
                shooter.uniqueId,
                Component.text("✦ HIT ", NamedTextColor.GREEN)
                    .append(Component.text("(${victimHpLeft}/${SplatoonSettings.inkMaxHp})", NamedTextColor.GRAY))
            )
        }

        // Victim: hurt confirmation (since we cancel vanilla damage, MC won't always play it).
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 0.6f, 1.1f)
        game.pushActionBarOverlay(
            victim.uniqueId,
            Component.text("✹ HIT ", NamedTextColor.RED)
                .append(Component.text("(${victimHpLeft}/${SplatoonSettings.inkMaxHp})", NamedTextColor.GRAY))
        )

        // Keep the HP bar synced immediately.
        game.syncHealthBar(victim)
    }

    private fun splatAndRespawn(player: Player, game: Game) {
        game.resetInkHp(player.uniqueId)
        player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }
        game.teleportToTeamSpawn(player)
        game.setSpawnProtection(player, SplatoonSettings.spawnProtectionAfterRespawnSeconds * 1000L)
        game.syncHealthBar(player)
        player.velocity = player.velocity.zero()
        player.fireTicks = 0
        player.foodLevel = 20
        player.saturation = 20f

        if (SplatoonSettings.spawnProtectionResistanceDurationTicks > 0) {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.RESISTANCE,
                    SplatoonSettings.spawnProtectionResistanceDurationTicks,
                    SplatoonSettings.spawnProtectionResistanceAmplifier,
                    false,
                    false,
                    false
                )
            )
        }
        player.noDamageTicks = SplatoonSettings.spawnProtectionNoDamageTicks
    }

    private fun getShooterUuid(str: String?): UUID? {
        if (str.isNullOrBlank()) return null
        return try {
            UUID.fromString(str)
        } catch (_: Exception) {
            null
        }
    }

    private fun explosivePaint(
        r: Double,
        location: org.bukkit.Location,
        world: World,
        game: Game,
        shooterId: UUID,
        paintTeam: Int,
        exclude: Block?
    ) {
        val blocks = mutableListOf<Block>()
        for (x in roundFromZero(location.x - r)..roundFromZero(location.x + r)) {
            for (y in roundFromZero(location.y - r)..roundFromZero(location.y + r)) {
                for (z in roundFromZero(location.z - r)..roundFromZero(location.z + r)) {
                    val b = world.getBlockAt(x, y, z)
                    if (b.type != Material.AIR && b.location.distance(location) <= r) blocks.add(b)
                }
            }
        }

        val paintable = setOf(
            Material.WHITE_CONCRETE,
            Material.RED_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.BLUE_CONCRETE
        )

        val matToTeam = mutableMapOf<Material, Int>()
        game.commandColors.forEach { (team, mat) -> matToTeam[mat] = team }

        val newMat = game.commandColors[paintTeam] ?: Material.WHITE_CONCRETE

        for (b in blocks) {
            if (exclude != null && b.x == exclude.x && b.y == exclude.y && b.z == exclude.z) continue
            if (!paintable.contains(b.type)) continue
            if (b.type == newMat) continue

            val oldTeam = matToTeam[b.type]
            if (oldTeam != null) {
                game.paintedCommand[oldTeam] = (game.paintedCommand[oldTeam] ?: 0) - 1
            }

            b.type = newMat

            val shooterBaseTeam = game.commands[shooterId]
            if (shooterBaseTeam != null) {
                val delta = when {
                    oldTeam == shooterBaseTeam && paintTeam != shooterBaseTeam -> -1
                    oldTeam != shooterBaseTeam && paintTeam == shooterBaseTeam -> 1
                    else -> 0
                }
                if (delta != 0) game.paintedPerson[shooterId] = (game.paintedPerson[shooterId] ?: 0) + delta
            }

            game.paintedCommand[paintTeam] = (game.paintedCommand[paintTeam] ?: 0) + 1
        }
    }

    private fun roundFromZero(n: Double): Int {
        return when {
            n > 0 -> ceil(n).toInt()
            n < 0 -> floor(n).toInt()
            else -> n.toInt()
        }
    }
}

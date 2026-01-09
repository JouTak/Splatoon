package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

class ProjectileHitListener : Listener {

    private val spawnProtectionRadius = 7.0
    private val spawnProtectionRadiusSq = spawnProtectionRadius * spawnProtectionRadius

    @EventHandler
    fun projectileHitEvent(event: ProjectileHitEvent) {
        val entity = event.entity
        if (entity.type != EntityType.SNOWBALL) return
        if (!entity.hasMetadata("paintKey")) return

        val shooterUuid = getShooterUuid(entity.getMetadata("shooterId").firstOrNull()?.asString())
        val shooter = if (shooterUuid != null) Bukkit.getPlayer(shooterUuid) else null
        if (shooter == null) return

        val game = GameManager.playerGame[shooter.uniqueId] ?: return
        val shooterTeam = game.commands[shooter.uniqueId] ?: return

        val paintTeam = entity.getMetadata("paintTeam").firstOrNull()?.asInt() ?: shooterTeam
        val isBomb = entity.hasMetadata("bombKey")
        val radius = if (isBomb) 5.0 else 1.5
        val killPaintRadius = if (isBomb) 5.0 else 3.0

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

            val victimProtectedByInk = isOnOwnInk(victim, game)
            val spawnSafe = isSpawnSafe(victim, game)

            val excludeUnder = if (victimProtectedByInk && paintTeam != victimTeam) victim.location.block else null
            explosivePaint(radius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, excludeUnder)

            if (victimProtectedByInk || spawnSafe) return
            if (paintTeam == victimTeam) return
            if (shooterTeam == victimTeam) return

            val hpLeft = game.damageInkHp(victim.uniqueId, 1)
            if (hpLeft <= 0) {
                game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                splatAndRespawn(victim, game)
                explosivePaint(killPaintRadius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, null)
            }
            return
        }

        if (hitBlock == null || hitFace == null) return

        val center = hitBlock.getRelative(hitFace).location

        if (!isBomb) {
            explosivePaint(radius, center, entity.world, game, shooter.uniqueId, paintTeam, null)
            return
        }

        val victims = entity.world.getNearbyEntities(center, radius, radius, radius)
            .filterIsInstance<Player>()
            .filter { GameManager.playerGame[it.uniqueId] == game }

        val protectedSnapshot = mutableMapOf<UUID, Boolean>()
        victims.forEach { v ->
            protectedSnapshot[v.uniqueId] = isOnOwnInk(v, game) || isSpawnSafe(v, game)
        }

        explosivePaint(radius, center, entity.world, game, shooter.uniqueId, paintTeam, null)

        victims.forEach { victim ->
            val victimTeam = game.commands[victim.uniqueId] ?: return@forEach
            val protected = protectedSnapshot[victim.uniqueId] == true
            if (protected) return@forEach
            if (paintTeam == victimTeam) return@forEach
            if (shooterTeam == victimTeam) return@forEach

            val hpLeft = game.damageInkHp(victim.uniqueId, 1)
            if (hpLeft <= 0) {
                game.kills[shooter.uniqueId] = (game.kills[shooter.uniqueId] ?: 0) + 1
                splatAndRespawn(victim, game)
                explosivePaint(killPaintRadius, victim.location, entity.world, game, shooter.uniqueId, paintTeam, null)
            }
        }
    }

    private fun isOnOwnInk(player: Player, game: Game): Boolean {
        val team = game.commands[player.uniqueId] ?: return false
        val under = player.location.clone().subtract(0.0, 0.1, 0.0).block
        val ownMat = game.commandColors[team] ?: return false
        return under.type == ownMat
    }

    private fun splatAndRespawn(player: Player, game: Game) {
        val w = Bukkit.getWorld(game.worldName) ?: return
        val spawn = w.spawnLocation

        game.resetInkHp(player.uniqueId)
        game.setSpawnProtection(player.uniqueId, 4000)

        player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }
        player.teleport(spawn)
        player.velocity = player.velocity.zero()
        player.fireTicks = 0
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f

        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 10, false, false, false))
        player.noDamageTicks = 60
    }

    private fun isSpawnSafe(player: Player, game: Game): Boolean {
        val w = Bukkit.getWorld(game.worldName) ?: return false
        if (player.world.name != w.name) return false
        if (game.isSpawnProtectionActive(player.uniqueId)) return true

        val spawn = w.spawnLocation
        val dx = player.location.x - spawn.x
        val dy = player.location.y - spawn.y
        val dz = player.location.z - spawn.z
        val distSq = dx * dx + dy * dy + dz * dz
        return distSq <= spawnProtectionRadiusSq
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

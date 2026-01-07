package ru.joutak.splatoon.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID

class BacillusHitListener(private val plugin: Plugin) : Listener {

    private val spawnProtectionRadius = 7.0
    private val spawnProtectionRadiusSq = spawnProtectionRadius * spawnProtectionRadius

    private val useCooldown: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler
    fun onSwing(event: PlayerAnimationEvent) {
        val damager = event.player

        val bacillusHand = findBacillus(damager) ?: return

        val now = System.currentTimeMillis()
        val last = useCooldown[damager.uniqueId]
        if (last != null && now - last < 250) return

        val target = raytracePlayer(damager, 3.2) ?: return

        if (tryApplyBacillus(damager, target, bacillusHand)) {
            useCooldown[damager.uniqueId] = now
            consumeBacillus(damager, bacillusHand)
        }
    }

    @EventHandler
    fun onHit(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val entity = event.entity

        if (damager !is Player) return
        if (entity !is Player) return

        val hand = findBacillus(damager) ?: return

        if (tryApplyBacillus(damager, entity, hand)) {
            event.isCancelled = true
            event.damage = 0.0
        }
    }

    private fun tryApplyBacillus(damager: Player, victim: Player, hand: BacillusHand): Boolean {
        val game = GameManager.playerGame[damager.uniqueId]
        val victimGame = GameManager.playerGame[victim.uniqueId]

        if (game != null && victimGame == game) {
            val attackerTeam = game.commands[damager.uniqueId] ?: return false
            val victimTeam = game.commands[victim.uniqueId] ?: return false
            if (attackerTeam == victimTeam) return false

            if (isSpawnSafe(victim, game)) return false

            game.applyAmmoOverride(victim.uniqueId, attackerTeam, 5_000)
            return true
        }

        val item = if (hand == BacillusHand.MAIN) damager.inventory.itemInMainHand else damager.inventory.itemInOffHand
        val meta = item.itemMeta
        val pdc = meta?.persistentDataContainer
        val adminAllowed = damager.hasPermission("splatoon.admin") && pdc != null && pdc.has(
            NamespacedKey(plugin, "splatoonAdmin"),
            PersistentDataType.BOOLEAN
        )
        if (!adminAllowed) return false

        val attackerTeam = pdc?.get(NamespacedKey(plugin, "adminTeam"), PersistentDataType.INTEGER) ?: 0
        GameManager.setAdminAmmoOverride(victim.uniqueId, attackerTeam, 5_000)
        return true
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

    private fun raytracePlayer(player: Player, maxDistance: Double): Player? {
        val start = player.eyeLocation
        val dir = start.direction

        val res = player.world.rayTraceEntities(start, dir, maxDistance, 0.3) { e ->
            e is Player && e.uniqueId != player.uniqueId
        } ?: return null

        return res.hitEntity as? Player
    }

    private fun findBacillus(player: Player): BacillusHand? {
        val key = NamespacedKey(plugin, "Bacillus")

        val main = player.inventory.itemInMainHand
        if (main.type == Material.AMETHYST_SHARD && main.hasItemMeta() &&
            main.itemMeta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
        ) return BacillusHand.MAIN

        val off = player.inventory.itemInOffHand
        if (off.type == Material.AMETHYST_SHARD && off.hasItemMeta() &&
            off.itemMeta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
        ) return BacillusHand.OFF

        return null
    }

    private fun consumeBacillus(player: Player, hand: BacillusHand) {
        if (hand == BacillusHand.MAIN) {
            val item = player.inventory.itemInMainHand
            if (item.amount <= 1) player.inventory.setItemInMainHand(null)
            else {
                item.amount = item.amount - 1
                player.inventory.setItemInMainHand(item)
            }
            return
        }

        val item = player.inventory.itemInOffHand
        if (item.amount <= 1) player.inventory.setItemInOffHand(null)
        else {
            item.amount = item.amount - 1
            player.inventory.setItemInOffHand(item)
        }
    }

    private enum class BacillusHand { MAIN, OFF }
}

package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.time.Duration
import java.util.UUID

class BacillusHitListener(private val plugin: Plugin) : Listener {

    private val spawnProtectionRadius = 7.0
    private val spawnProtectionRadiusSq = spawnProtectionRadius * spawnProtectionRadius

    private val useCooldown: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler
    fun onSwing(event: PlayerAnimationEvent) {
        val player = event.player
        val game = GameManager.playerGame[player.uniqueId] ?: return

        val hand = findBacillus(player) ?: return

        val now = System.currentTimeMillis()
        val last = useCooldown[player.uniqueId]
        if (last != null && now - last < 250) return

        val target = raytracePlayer(player, 3.2) ?: return
        if (GameManager.playerGame[target.uniqueId] != game) return

        val attackerTeam = game.commands[player.uniqueId] ?: return
        val victimTeam = game.commands[target.uniqueId] ?: return
        if (attackerTeam == victimTeam) return

        if (isSpawnSafe(target, game)) return

        useCooldown[player.uniqueId] = now

        game.applyAmmoOverride(target.uniqueId, attackerTeam, 5_000)
        showBacillusTitles(player, target)

        consumeBacillus(player, hand)
    }

    private fun showBacillusTitles(attacker: Player, victim: Player) {
        val t = Title.title(
            Component.text("☣ Бацилла!", NamedTextColor.LIGHT_PURPLE),
            Component.text("Вы заражены на 5 секунд", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(150))
        )
        victim.showTitle(t)

        val t2 = Title.title(
            Component.text("☣ Bacillus", NamedTextColor.LIGHT_PURPLE),
            Component.text("Цель заражена!", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(900), Duration.ofMillis(150))
        )
        attacker.showTitle(t2)
    }

    private fun raytracePlayer(player: Player, maxDistance: Double): Player? {
        val start = player.eyeLocation
        val dir = start.direction
        val res = player.world.rayTraceEntities(start, dir, maxDistance, 0.3) { e ->
            e is Player && e.uniqueId != player.uniqueId
        } ?: return null

        val hit = res.hitEntity ?: return null
        return hit as? Player
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

    private fun findBacillus(player: Player): BacillusHand? {
        val key = NamespacedKey(plugin, "Bacillus")

        val off = player.inventory.itemInOffHand
        if (off.type == Material.AMETHYST_SHARD && off.hasItemMeta() &&
            off.itemMeta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
        ) return BacillusHand.OFF

        val main = player.inventory.itemInMainHand
        if (main.type == Material.AMETHYST_SHARD && main.hasItemMeta() &&
            main.itemMeta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
        ) return BacillusHand.MAIN

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

package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID

/**
 * Пушка на арбалете:
 * - арбалет всегда визуально "заряжен" (чтобы моделька не дёргалась)
 * - стрельба только по ПКМ (без ванильного натяжения/перезарядки)
 * - ванильные звуки арбалета полностью не используются
 */
class SplatGunBowListener(private val plugin: Plugin) : Listener {

    private val gunKey = NamespacedKey(plugin, "splatGun")
    private val fallbackAmmoKey = NamespacedKey(plugin, "splatAmmo")
    private val adminKey = NamespacedKey(plugin, "splatoonAdmin")
    private val adminTeamKey = NamespacedKey(plugin, "adminTeam")

    private val nextShotAtMs = mutableMapOf<UUID, Long>()

    // Скорострельность (по кликам)
    private val clickCooldownMs = 170L

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return

        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(adminKey, PersistentDataType.BOOLEAN)
        if (game == null && !isAdminUse) return

        // Полностью запрещаем ванильное использование арбалета (без натяжения/звуков/перезарядки)
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        event.isCancelled = true

        // При первом боевом действии — снимаем спавнпротекшн (deathmatch).
        if (game != null) {
            game.cancelSpawnProtectionByAction(player)
        }

        // Держим арбалет всегда "заряженным" визуально (чтобы не скакал по экрану)
        setChargedVisual(player, true)

        val (baseTeam, paintTeam) = resolveTeams(player, game, pdc) ?: return
        ensureAmmoFallback(player, teamToColorName(paintTeam))

        val now = System.currentTimeMillis()
        tryShoot(player, game, isAdminUse, baseTeam, paintTeam, now)
    }

    // Ванильный выстрел арбалетом полностью отменяем (мы спавним свои snowball).
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    fun onShoot(event: EntityShootBowEvent) {
        val item = event.bow ?: return
        if (item.type != Material.CROSSBOW) return

        val shooter = event.entity as? Player ?: return
        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(gunKey, PersistentDataType.BOOLEAN)) return

        event.isCancelled = true
        event.setConsumeItem(false)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        nextShotAtMs.remove(event.player.uniqueId)
    }

    private fun tryShoot(
        player: Player,
        game: Game?,
        isAdminUse: Boolean,
        baseTeam: Int,
        paintTeam: Int,
        now: Long
    ) {
        val next = nextShotAtMs[player.uniqueId] ?: 0L
        if (now < next) return
        nextShotAtMs[player.uniqueId] = now + clickCooldownMs

        fireOnce(player, game, isAdminUse, baseTeam, paintTeam)
    }

    private fun fireOnce(player: Player, game: Game?, isAdminUse: Boolean, baseTeam: Int, paintTeam: Int) {
        val colorName = teamToColorName(paintTeam)
        ensureAmmoFallback(player, colorName)

        val projectileItem = createProjectileItem(colorName)
        val dir = player.eyeLocation.direction.normalize()
        val muzzle = muzzleLocation(player.eyeLocation, dir)

        // Мягкий "красящий" звук вместо любых звуков арбалета.
        // Важно: используем точный key как в /playsound, чтобы не зависеть от enum.
        player.world.playSound(muzzle, "minecraft:item.dye.use", 1.0f, 1.0f)

        player.world.spawn(muzzle, Snowball::class.java) { snowball ->
            snowball.item = projectileItem
            snowball.setGravity(!SplatoonSettings.gunDisableGravity)
            snowball.velocity = dir.clone().multiply(SplatoonSettings.gunVelocity)
            snowball.shooter = player

            snowball.setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            snowball.setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
            snowball.setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
            snowball.setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
        }
    }

    private fun resolveTeams(
        player: Player,
        game: Game?,
        pdc: org.bukkit.persistence.PersistentDataContainer
    ): Pair<Int, Int>? {
        val baseTeam = if (game != null) {
            game.commands[player.uniqueId] ?: return null
        } else {
            pdc.get(adminTeamKey, PersistentDataType.INTEGER) ?: 0
        }

        val paintTeam = if (game != null) {
            game.getAmmoTeam(player.uniqueId) ?: baseTeam
        } else {
            GameManager.getAdminAmmoTeam(player.uniqueId, baseTeam)
        }

        return baseTeam to paintTeam
    }

    private fun createProjectileItem(name: String): ItemStack {
        val stack = ItemStack(Material.SNOWBALL, 1)
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name))
        return stack
    }

    private fun teamToColorName(team: Int): String {
        return when (team) {
            0 -> "Red"
            3 -> "Blue"
            2 -> "Green"
            1 -> "Yellow"
            else -> "Red"
        }
    }

    private fun ensureAmmoFallback(player: Player, colorName: String? = null) {
        val inv = player.inventory

        var ammoSlot = -1
        for (i in 0 until inv.size) {
            val it = inv.getItem(i) ?: continue
            if (it.type == Material.AIR || !it.hasItemMeta()) continue
            if (it.itemMeta.persistentDataContainer.has(fallbackAmmoKey, PersistentDataType.BOOLEAN)) {
                ammoSlot = i
                break
            }
        }

        val item = if (ammoSlot >= 0) {
            inv.getItem(ammoSlot) ?: return
        } else {
            val created = ItemStack(Material.ARROW, 1)
            val m = created.itemMeta
            m.persistentDataContainer.set(fallbackAmmoKey, PersistentDataType.BOOLEAN, true)
            created.itemMeta = m

            val slot = firstEmptyMainInvSlot(inv) ?: 35
            inv.setItem(slot, created)
            ammoSlot = slot
            created
        }

        if (colorName != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, Component.text(colorName))
            inv.setItem(ammoSlot, item)
        }
    }

    private fun firstEmptyMainInvSlot(inv: org.bukkit.inventory.PlayerInventory): Int? {
        for (i in 9..35) {
            val it = inv.getItem(i)
            if (it == null || it.type == Material.AIR) return i
        }
        return null
    }

    private fun setChargedVisual(player: Player, charged: Boolean) {
        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return
        if (!item.hasItemMeta()) return

        val meta = item.itemMeta
        if (!meta.persistentDataContainer.has(gunKey, PersistentDataType.BOOLEAN)) return

        setChargedOnItem(item, charged)
        player.inventory.setItemInMainHand(item)
    }

    /**
     * Делает арбалет визуально "заряженным" без привязки к конкретному Paper/Bukkit API.
     */
    private fun setChargedOnItem(item: ItemStack, charged: Boolean) {
        val meta = item.itemMeta as? CrossbowMeta ?: return

        fun clearCharged() {
            meta.javaClass.methods.firstOrNull { it.name == "clearChargedProjectiles" && it.parameterCount == 0 }?.let { m ->
                runCatching { m.invoke(meta) }
                return
            }
            meta.javaClass.methods.firstOrNull { it.name == "setChargedProjectiles" && it.parameterCount == 1 }?.let { m ->
                runCatching { m.invoke(meta, emptyList<ItemStack>()) }
                return
            }
            runCatching {
                val list = meta.chargedProjectiles
                if (list is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (list as MutableList<ItemStack>).clear()
                }
            }
        }

        fun setDummyArrow() {
            meta.javaClass.methods.firstOrNull { it.name == "setChargedProjectiles" && it.parameterCount == 1 }?.let { m ->
                runCatching { m.invoke(meta, listOf(ItemStack(Material.ARROW, 1))) }
                return
            }
            meta.javaClass.methods.firstOrNull { it.name == "addChargedProjectile" && it.parameterCount == 1 }?.let { m ->
                runCatching { m.invoke(meta, ItemStack(Material.ARROW, 1)) }
                return
            }
        }

        if (charged) {
            if (meta.chargedProjectiles.isNotEmpty()) return
            clearCharged()
            setDummyArrow()
        } else {
            if (meta.chargedProjectiles.isEmpty()) return
            clearCharged()
        }

        item.itemMeta = meta
    }

    private fun muzzleLocation(eye: Location, dir: Vector): Location {
        val up = Vector(0.0, 1.0, 0.0)
        var right = dir.clone().crossProduct(up)
        if (right.lengthSquared() < 1e-6) {
            right = Vector(1.0, 0.0, 0.0)
        }
        right.normalize()

        // Смещаем старт ближе к пушке: вперёд + вправо + чуть вниз (к руке)
        return eye.clone()
            .add(dir.clone().multiply(0.50))
            .add(right.multiply(0.32))
            .add(0.0, -0.40, 0.0)
    }

}

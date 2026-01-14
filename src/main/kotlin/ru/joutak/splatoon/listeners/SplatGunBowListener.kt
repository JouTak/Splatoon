package ru.joutak.splatoon.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
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
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import ru.joutak.splatoon.config.SplatoonSettings
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import java.util.UUID
import kotlin.random.Random

/**
 * Пушка на арбалете:
 * - НЕТ "тумблера": 1 клик без удержания = 1 выстрел и быстрое завершение тикера
 * - авто-огонь только при удержании ПКМ
 * - визуально арбалет "заряжен" пока активен режим стрельбы (клик/удержание)
 */
class SplatGunBowListener(private val plugin: Plugin) : Listener {

    private val gunKey = NamespacedKey(plugin, "splatGun")
    private val fallbackAmmoKey = NamespacedKey(plugin, "splatAmmo")
    private val adminKey = NamespacedKey(plugin, "splatoonAdmin")
    private val adminTeamKey = NamespacedKey(plugin, "adminTeam")

    private val firingTasks = mutableMapOf<UUID, BukkitTask>()
    private val nextShotAtMs = mutableMapOf<UUID, Long>()
    private val lastClickAtMs = mutableMapOf<UUID, Long>()

    // Через сколько после клика без удержания мы гасим режим стрельбы (чтобы не мигало/не дёргалось)
    private val clickGraceMs = 220L

    // Скорострельность
    private val clickCooldownMs = 170L   // выстрелы по кликам
    private val holdIntervalMs = 300L    // авто-огонь при удержании (медленнее)

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

        // Запретить взаимодействие с блоком, но позволить использовать предмет (важно для удержания ПКМ).
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.ALLOW)
        }

        // При первом боевом действии — снимаем спавнпротекшн (deathmatch).
        if (game != null) {
            game.cancelSpawnProtectionByAction(player)
        }

        val (baseTeam, paintTeam) = resolveTeams(player, game, pdc) ?: return
        ensureAmmoFallback(player, teamToColorName(paintTeam))

        val now = System.currentTimeMillis()
        lastClickAtMs[player.uniqueId] = now

        // 1 клик = 1 выстрел (с кулдауном)
        tryShoot(player, game, isAdminUse, baseTeam, paintTeam, now, clickCooldownMs)

        // Запускаем тикер: при удержании ПКМ будет авто-огонь, без удержания он быстро сам остановится.
        startFiringIfNeeded(player, game, isAdminUse)
    }

    // Ванильный выстрел арбалетом полностью отменяем (мы спавним свои snowball).
    @EventHandler(ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val item = event.bow ?: return
        if (item.type != Material.CROSSBOW) return

        val shooter = event.entity as? Player ?: return
        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(gunKey, PersistentDataType.BOOLEAN)) return

        event.isCancelled = true
        event.setConsumeItem(false)

        // stopFiring здесь НЕ делаем: иначе при удержании возможны микроподёргивания.
        // Остановка у нас строго по отпусканию удержания/отсутствию кликов (см. тикер).
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        stopFiring(event.player.uniqueId)
    }

    private fun startFiringIfNeeded(player: Player, game: Game?, isAdminUse: Boolean) {
        val uuid = player.uniqueId
        if (firingTasks.containsKey(uuid)) return

        // Визуально "натянутый/заряженный" пока режим активен
        setChargedVisual(player, true)

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!shouldContinue(player)) {
                stopFiring(uuid)
                return@Runnable
            }
            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return@Runnable

            val now = System.currentTimeMillis()

            // Авто-огонь только при удержании
            val holding = player.isHandRaised

            if (!holding) {
                // Без удержания — быстро гасим (чтобы одиночный клик не превращался в "автомат")
                val lastClick = lastClickAtMs[uuid] ?: 0L
                if (now - lastClick > clickGraceMs) {
                    stopFiring(uuid)
                }
                return@Runnable
            }

            val item = player.inventory.itemInMainHand
            val meta = item.itemMeta ?: return@Runnable
            val teams = resolveTeams(player, game, meta.persistentDataContainer) ?: return@Runnable

            tryShoot(
                player = player,
                game = game,
                isAdminUse = isAdminUse,
                baseTeam = teams.first,
                paintTeam = teams.second,
                now = now,
                cooldownMs = holdIntervalMs
            )
        }, 0L, 1L)

        firingTasks[uuid] = task
    }

    private fun stopFiring(uuid: UUID) {
        firingTasks.remove(uuid)?.cancel()
        nextShotAtMs.remove(uuid)
        lastClickAtMs.remove(uuid)

        val p = plugin.server.getPlayer(uuid)
        if (p != null) {
            setChargedVisual(p, false)
        }
    }

    private fun tryShoot(
        player: Player,
        game: Game?,
        isAdminUse: Boolean,
        baseTeam: Int,
        paintTeam: Int,
        now: Long,
        cooldownMs: Long
    ) {
        val next = nextShotAtMs[player.uniqueId] ?: 0L
        if (now < next) return
        nextShotAtMs[player.uniqueId] = now + cooldownMs

        fireOnce(player, game, isAdminUse, baseTeam, paintTeam)
    }

    private fun fireOnce(player: Player, game: Game?, isAdminUse: Boolean, baseTeam: Int, paintTeam: Int) {
        val colorName = teamToColorName(paintTeam)
        ensureAmmoFallback(player, colorName)

        val projectileItem = createProjectileItem(colorName)
        val dir = player.eyeLocation.direction.normalize()
        val muzzle = muzzleLocation(player.eyeLocation, dir)

        player.world.playSound(
            muzzle,
            Sound.ENTITY_SNOWBALL_THROW,
            0.6f,
            (1.2f + Random.nextFloat() * 0.2f)
        )

        player.world.spawn(muzzle, Snowball::class.java).apply {
            item = projectileItem
            setGravity(!SplatoonSettings.gunDisableGravity)
            velocity = dir.clone().multiply(SplatoonSettings.gunVelocity)
            shooter = player

            setMetadata("paintKey", FixedMetadataValue(plugin, 1))
            setMetadata("paintTeam", FixedMetadataValue(plugin, paintTeam))
            setMetadata("baseTeam", FixedMetadataValue(plugin, baseTeam))
            setMetadata("shooterId", FixedMetadataValue(plugin, player.uniqueId.toString()))
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

    private fun shouldContinue(player: Player): Boolean {
        if (!player.isOnline) return false

        val item = player.inventory.itemInMainHand
        if (item.type != Material.CROSSBOW) return false

        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        if (!pdc.has(gunKey, PersistentDataType.BOOLEAN)) return false

        val game = GameManager.playerGame[player.uniqueId]
        val isAdminUse = game == null && player.hasPermission("splatoon.admin") && pdc.has(adminKey, PersistentDataType.BOOLEAN)
        return game != null || isAdminUse
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
        val right = up.clone().crossProduct(dir).normalize()
        return eye.clone()
            .add(dir.clone().multiply(0.35))
            .add(right.multiply(0.18))
            .add(0.0, -0.35, 0.0)
    }
}

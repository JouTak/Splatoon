package ru.joutak.splatoon.scripts

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team
import org.bukkit.util.Vector
import ru.joutak.minigames.MiniGamesAPI
import ru.joutak.minigames.results.model.MatchResult
import ru.joutak.minigames.results.model.Metric
import ru.joutak.minigames.results.model.PlayerResult
import ru.joutak.minigames.results.model.TeamResult
import ru.joutak.splatoon.SplatoonPlugin
import ru.joutak.splatoon.config.SpawnPoint
import ru.joutak.splatoon.config.SplatoonSettings
import java.time.Duration
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

class Game(var worldName: String, val arenaId: String, private val teamSpawns: Map<Int, List<SpawnPoint>>) {

    // Results (shared DB via MiniGamesAPI)
    val matchId: UUID = UUID.randomUUID()
    val startedAtMs: Long = System.currentTimeMillis()
    @Volatile
    private var resultsSent: Boolean = false

    val paintedCommand: MutableMap<Int, Int> = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    var paintedPerson: MutableMap<UUID, Int> = mutableMapOf()
    val kills: MutableMap<UUID, Int> = mutableMapOf()

    val commandColors: Map<Int, Material> = mapOf(
        0 to Material.RED_CONCRETE,
        3 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        1 to Material.YELLOW_CONCRETE
    )
    var commands: MutableMap<UUID, Int> = mutableMapOf()

    private val playerNames: MutableMap<UUID, String> = mutableMapOf()
    private val playerTeamsSnapshot: MutableMap<UUID, Int> = mutableMapOf()
    private val playerJoinedAtMs: MutableMap<UUID, Long> = mutableMapOf()
    private val playerLeftAtMs: MutableMap<UUID, Long> = mutableMapOf()

    private var countdownTask: BukkitTask? = null
    private var gameTimerTask: BukkitTask? = null
    private var boostTimerTask: BukkitTask? = null
    private var scoreboardUpdateTask: BukkitTask? = null
    private var actionBarTask: BukkitTask? = null

    private var timeLeft = 0
    private val totalTime = SplatoonSettings.gameDurationSeconds

    private var bossBar: BossBar? = null
    private var activeTeams: List<Int> = listOf()

    private val playerScoreboards: MutableMap<UUID, org.bukkit.scoreboard.Scoreboard> = mutableMapOf()
    private val playerObjectives: MutableMap<UUID, org.bukkit.scoreboard.Objective> = mutableMapOf()

    var totalPaintableBlocks: Int = 0

    val ammoOverride: MutableMap<UUID, Pair<Int, Long>> = mutableMapOf()

    val spawnProtectedUntil: MutableMap<UUID, Long> = mutableMapOf()
    private val spawnProtectedOrigin: MutableMap<UUID, Vector> = mutableMapOf()
    private val spawnProtectionMoved: MutableMap<UUID, Boolean> = mutableMapOf()

    val maxInkHp: Int = SplatoonSettings.inkMaxHp
    private val inkHp: MutableMap<UUID, Int> = mutableMapOf()

    private val inkRegenCarry: MutableMap<UUID, Double> = mutableMapOf()
    private val inkLastDamageAt: MutableMap<UUID, Long> = mutableMapOf()

    private val hitMarkerActionbarTicks = 30
    private val actionBarOverlayUntilMs: MutableMap<UUID, Long> = mutableMapOf()
    private val actionBarOverlayText: MutableMap<UUID, Component> = mutableMapOf()

    private var ended = false

    fun shutdownGame() {
        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
        boostTimerTask?.cancel()
        actionBarTask?.cancel()

        removeBossBar()
        clearScoreboards()

        ammoOverride.clear()
        spawnProtectedUntil.clear()
        spawnProtectedOrigin.clear()
        spawnProtectionMoved.clear()
        inkHp.clear()
        inkRegenCarry.clear()
        inkLastDamageAt.clear()
        actionBarOverlayUntilMs.clear()
        actionBarOverlayText.clear()

        playerNames.clear()
        playerTeamsSnapshot.clear()
        playerJoinedAtMs.clear()
        playerLeftAtMs.clear()

        val emptyScoreboard = Bukkit.getScoreboardManager().newScoreboard
        val lobbyWorld = Bukkit.getWorld(SplatoonSettings.lobbyWorldName)
        val spawn = lobbyWorld?.spawnLocation ?: Bukkit.getWorlds()[0].spawnLocation

        commands.keys.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.scoreboard = emptyScoreboard
            player.inventory.clear()
            restoreVanillaHealth(player)
            player.foodLevel = 20
            player.saturation = 20f
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }
            player.teleport(spawn)
        }
    }

    fun pushActionBarOverlay(uuid: UUID, component: Component, durationTicks: Int = hitMarkerActionbarTicks) {
        if (durationTicks <= 0) return
        actionBarOverlayText[uuid] = component
        actionBarOverlayUntilMs[uuid] = System.currentTimeMillis() + durationTicks * 50L
    }

    fun startGame(worldName: String) {
        activeTeams = commands.values.toSet().sorted()

        // Snapshot players for results (including possible leavers).
        val joinAt = startedAtMs
        commands.forEach { (uuid, team) ->
            playerTeamsSnapshot[uuid] = team
            playerJoinedAtMs.putIfAbsent(uuid, joinAt)
            playerLeftAtMs.remove(uuid)
            val p = Bukkit.getPlayer(uuid)
            if (p != null) playerNames[uuid] = p.name
        }

        commands.keys.forEach { uuid ->
            inkHp[uuid] = maxInkHp
            inkRegenCarry.remove(uuid)
            inkLastDamageAt.remove(uuid)
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.inventory.clear()
            ensureInkHealth(player)
            syncHealthBar(player)
            teleportToTeamSpawn(player)
            setSpawnProtection(player, SplatoonSettings.spawnProtectionAfterRespawnSeconds * 1000L)
        }

        startCountdown(worldName)
    }

    fun markPlayerLeft(uuid: UUID) {
        if (!playerJoinedAtMs.containsKey(uuid)) {
            playerJoinedAtMs[uuid] = startedAtMs
        }

        val team = playerTeamsSnapshot[uuid] ?: commands[uuid]
        if (team != null) playerTeamsSnapshot[uuid] = team

        if (!playerNames.containsKey(uuid)) {
            val p = Bukkit.getPlayer(uuid)
            if (p != null) playerNames[uuid] = p.name
        }

        playerLeftAtMs.putIfAbsent(uuid, System.currentTimeMillis())
    }

    fun endGame(worldName: String) {
        if (ended) return
        ended = true

        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
        boostTimerTask?.cancel()
        actionBarTask?.cancel()

        removeBossBar()
        clearScoreboards()

        ammoOverride.clear()
        spawnProtectedUntil.clear()
        spawnProtectedOrigin.clear()
        spawnProtectionMoved.clear()
        inkHp.clear()
        inkRegenCarry.clear()
        inkLastDamageAt.clear()
        actionBarOverlayUntilMs.clear()
        actionBarOverlayText.clear()

        val winner = determineWinner()

        sendMatchResultsIfNeeded(winner)

        Bukkit.getScheduler().runTask(SplatoonPlugin.instance, Runnable {
            showWinnerAnnouncement(winner)
        })

        playSoundToAllPlayers(
            Sound.sound(
                Key.key("ui.toast.challenge_complete"),
                Sound.Source.MASTER,
                1.0f,
                1.0f
            )
        )

        val emptyScoreboard = Bukkit.getScoreboardManager().newScoreboard

        Bukkit.getScheduler().runTaskLater(SplatoonPlugin.instance, Runnable {
            val lobbyWorld = Bukkit.getWorld(SplatoonSettings.lobbyWorldName)
            if (lobbyWorld == null) {
                SplatoonPlugin.instance.logger.severe(
                    "Не удалось найти мир лобби: ${SplatoonSettings.lobbyWorldName}. Удаление игры остановлено."
                )
                GameManager.deleteGame(this.worldName, this)
                return@Runnable
            }

            commands.keys.forEach { playerId ->
                val player = Bukkit.getPlayer(playerId) ?: return@forEach
                player.scoreboard = emptyScoreboard
                player.inventory.clear()
                restoreVanillaHealth(player)
                player.foodLevel = 20
                player.saturation = 20f
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }
                player.teleport(lobbyWorld.spawnLocation)
            }

            GameManager.deleteGame(this.worldName, this)
        }, SplatoonSettings.returnToLobbyDelaySeconds * 20L)
    }

    private fun sendMatchResultsIfNeeded(winnerTeam: Int) {
        if (resultsSent) return
        resultsSent = true

        val endedAtMs = System.currentTimeMillis()

        val allTeams = (playerTeamsSnapshot.values + commands.values).toSet().sorted()

        // Scores are in percent (0..100) if totalPaintableBlocks is known.
        val scoreByTeam = mutableMapOf<Int, Double>()
        allTeams.forEach { t ->
            val blocks = paintedCommand[t] ?: 0
            val pct = if (totalPaintableBlocks > 0) blocks * 100.0 / totalPaintableBlocks else blocks.toDouble()
            scoreByTeam[t] = pct
        }

        val sortedTeams = allTeams.sortedWith(compareByDescending<Int> { scoreByTeam[it] ?: 0.0 }.thenBy { it })
        val placementByTeam = mutableMapOf<Int, Int>()
        var place = 1
        sortedTeams.forEach { t ->
            placementByTeam[t] = place
            place++
        }

        val teamPlayerCounts = mutableMapOf<Int, Int>()
        playerTeamsSnapshot.values.forEach { t ->
            teamPlayerCounts[t] = (teamPlayerCounts[t] ?: 0) + 1
        }

        val teams = allTeams.map { t ->
            val blocks = paintedCommand[t] ?: 0
            val pct = scoreByTeam[t] ?: 0.0
            TeamResult(
                teamId = t + 1,
                placement = placementByTeam[t],
                isWinner = t == winnerTeam,
                score = pct,
                metrics = listOf(
                    Metric.int("paint_blocks", blocks.toLong()),
                    Metric.real("paint_percent", pct),
                    Metric.int("players", (teamPlayerCounts[t] ?: 0).toLong())
                )
            )
        }

        val allPlayers = mutableSetOf<UUID>()
        allPlayers.addAll(playerTeamsSnapshot.keys)
        allPlayers.addAll(commands.keys)
        allPlayers.addAll(paintedPerson.keys)
        allPlayers.addAll(kills.keys)

        val players = allPlayers.map { uuid ->
            val team = playerTeamsSnapshot[uuid] ?: commands[uuid]

            val name = playerNames[uuid]
                ?: Bukkit.getPlayer(uuid)?.name
                ?: Bukkit.getOfflinePlayer(uuid).name

            val paintBlocks = paintedPerson[uuid] ?: 0
            val paintPct = if (totalPaintableBlocks > 0) paintBlocks * 100.0 / totalPaintableBlocks else paintBlocks.toDouble()
            val k = kills[uuid] ?: 0

            PlayerResult(
                playerUuid = uuid,
                playerName = name,
                teamId = team?.plus(1),
                isWinner = team != null && team == winnerTeam,
                joinedAtMs = playerJoinedAtMs[uuid],
                leftAtMs = playerLeftAtMs[uuid],
                metrics = listOf(
                    Metric.int("kills", k.toLong()),
                    Metric.int("paint_blocks", paintBlocks.toLong()),
                    Metric.real("paint_percent", paintPct),
                )
            )
        }

        val result = MatchResult(
            matchId = matchId,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            mapKey = arenaId,
            teams = teams,
            players = players
        )

        // Safe when results are disabled; do not block the main thread.
        MiniGamesAPI.recordMatchResult(result)
    }

    private fun determineWinner(): Int {
        var maxScore = -1
        var winningTeam = 0
        paintedCommand.forEach { (team, score) ->
            if (score > maxScore) {
                maxScore = score
                winningTeam = team
            }
        }
        return winningTeam
    }

    private fun startCountdown(worldName: String) {
        var countdown = 6

        countdownTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            when (countdown) {
                6 -> {
                    val w = Bukkit.getWorld(this.worldName)
                    val targets = (w?.players ?: emptyList()).toMutableList()
                    commands.keys.forEach { id ->
                        val p = Bukkit.getPlayer(id)
                        if (p != null && !targets.contains(p)) targets.add(p)
                    }

                    targets.forEach { p ->
                        val colors = mapOf(
                            0 to Component.text("Ваша команда: Красные!", NamedTextColor.RED),
                            3 to Component.text("Ваша команда: Синие!", NamedTextColor.BLUE),
                            2 to Component.text("Ваша команда: Зелёные!", NamedTextColor.GREEN),
                            1 to Component.text("Ваша команда: Жёлтые!", NamedTextColor.YELLOW)
                        )
                        val subtitle = colors[commands[p.uniqueId]]
                            ?: Component.text("Матч начинается!", NamedTextColor.GRAY)

                        val titleColor = when (commands[p.uniqueId]) {
                            0 -> NamedTextColor.RED
                            1 -> NamedTextColor.YELLOW
                            2 -> NamedTextColor.GREEN
                            3 -> NamedTextColor.BLUE
                            else -> NamedTextColor.GOLD
                        }

                        val titleObj = Title.title(
                            Component.text("ПОДГОТОВКА!", titleColor),
                            subtitle,
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(200))
                        )
                        p.showTitle(titleObj)
                    }

                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1.0f, 1.0f)
                    )
                }

                3 -> {
                    showTitleToWorldPlayers(
                        Component.text("3", NamedTextColor.YELLOW),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1.0f, 1.0f)
                    )
                }

                2 -> {
                    showTitleToWorldPlayers(
                        Component.text("2", NamedTextColor.GOLD),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1.0f, 1.2f)
                    )
                }

                1 -> {
                    showTitleToWorldPlayers(
                        Component.text("1", NamedTextColor.RED),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1.0f, 1.4f)
                    )
                }

                0 -> {
                    showTitleToWorldPlayers(
                        Component.text("СТАРТ!", NamedTextColor.GREEN),
                        Component.text("Закрашивайте территорию!", NamedTextColor.GRAY)
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 1.0f, 1.0f)
                    )

                    giveSplatGuns()
                    sendStartInstructions()

                    startMainTimer(worldName)
                    startBoostTimer()
                    startActionBarLoop()

                    countdownTask?.cancel()
                }
            }
            countdown--
        }, 0L, 20L)
    }

    private fun sendStartInstructions() {
        commands.keys.forEach { id ->
            val p = Bukkit.getPlayer(id) ?: return@forEach
            p.sendMessage(Component.text("§6§lSplatoon §7— закрась арену своим цветом и набери больше %!"))
            p.sendMessage(Component.text("§f• §eПКМ пушкой §7— выстрел краской (Противников можно взорвать, у вас ${maxInkHp} ХП"))
            p.sendMessage(Component.text("§f• §eПКМ бомбочкой §7— взрыв краски"))
            p.sendMessage(
                Component.text(
                    "§f• §dБацилла §7 — §eударь игрока (ЛКМ), и он будет стрелять твоим цветом ${SplatoonSettings.bacillusDurationSeconds} секунд"
                )
            )
            p.sendMessage(Component.text("§f• §aУдерживая shift на своей краске §7вы скрываетесь и лечитесь ❤"))
        }
    }

    private fun startActionBarLoop() {
        actionBarTask?.cancel()
        actionBarTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            commands.keys.forEach { id ->
                val p = Bukkit.getPlayer(id) ?: return@forEach

                // Keep the HP bar strictly in sync with Ink HP and apply squid regen on own ink.
                syncHealthBar(p)
                keepFoodFull(p)
                tickInkRegen(p)

                updateSpawnProtectionMovement(p)
                val spawnSafe = isSpawnSafe(p)
                updateSpawnGlow(p, spawnSafe)
                p.sendActionBar(buildActionBar(p, spawnSafe))
            }
        }, 0L, SplatoonSettings.actionbarUpdateTicks)
    }

    private fun keepFoodFull(player: Player) {
        if (player.foodLevel != 20) player.foodLevel = 20
        if (player.saturation < 20f) player.saturation = 20f
        if (player.exhaustion != 0f) player.exhaustion = 0f
    }

    private fun tickInkRegen(player: Player) {
        if (!SplatoonSettings.inkRegenEnabled) return
        val uuid = player.uniqueId
        val cur = getInkHp(uuid)
        if (cur >= maxInkHp) {
            inkRegenCarry.remove(uuid)
            return
        }

        // Regen only while sneaking on own ink (squid mode).
        if (!player.isSneaking) {
            inkRegenCarry.remove(uuid)
            return
        }

        val invisEnabled = SplatoonSettings.sneakOnInkInvisibilityAmplifier >= 0
        val squidEffectOk = if (invisEnabled) {
            player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        } else {
            player.hasPotionEffect(PotionEffectType.SPEED)
        }
        if (!squidEffectOk) {
            inkRegenCarry.remove(uuid)
            return
        }

        val now = System.currentTimeMillis()
        val lastDamage = inkLastDamageAt[uuid] ?: 0L
        val delayMs = SplatoonSettings.inkRegenDelayAfterDamageSeconds * 1000L
        if (delayMs > 0 && now - lastDamage < delayMs) return

        val dtSeconds = SplatoonSettings.actionbarUpdateTicks.toDouble() / 20.0
        val add = SplatoonSettings.inkRegenRatePerSecond * dtSeconds
        if (add <= 0.0) return

        var carry = (inkRegenCarry[uuid] ?: 0.0) + add
        var hp = cur
        while (carry >= 1.0 && hp < maxInkHp) {
            hp += 1
            carry -= 1.0
        }

        inkRegenCarry[uuid] = carry
        if (hp != cur) {
            inkHp[uuid] = hp
            syncHealthBar(player)
        }
    }


    private fun buildActionBar(player: Player, spawnSafe: Boolean): Component {
        var hasAny = false
        // IMPORTANT: keep this typed as Component.
        // In some Adventure/Paper versions Component.empty() is TextComponent,
        // and Kotlin may infer TextComponent here, causing type mismatches on later assignments.
        var c: Component = Component.empty()

        val base = commands[player.uniqueId]
        val ov = ammoOverride[player.uniqueId]
        if (base != null && ov != null && ov.first != base) {
            val now = System.currentTimeMillis()
            val leftMs = (ov.second - now).coerceAtLeast(0)
            val leftSec = ceil(leftMs / 1000.0).toInt()
            c = c.append(Component.text("☣ Bacillus ${leftSec}с", NamedTextColor.LIGHT_PURPLE))
            hasAny = true
        }

        if (spawnSafe) {
            if (hasAny) c = c.append(Component.text("  "))
            c = c.append(Component.text("SPAWN", NamedTextColor.GREEN))
            hasAny = true
        }

        val now = System.currentTimeMillis()
        val overlayUntil = actionBarOverlayUntilMs[player.uniqueId] ?: 0L
        val overlay = if (overlayUntil > now) actionBarOverlayText[player.uniqueId] else null
        if (overlayUntil <= now) {
            actionBarOverlayUntilMs.remove(player.uniqueId)
            actionBarOverlayText.remove(player.uniqueId)
        }

        if (overlay != null) {
            c = if (hasAny) overlay.append(Component.text("  ")).append(c) else overlay
            hasAny = true
        }

        return if (hasAny) c else Component.empty()
    }

    fun getInkHp(uuid: UUID): Int {
        return inkHp[uuid] ?: maxInkHp
    }

    fun resetInkHp(uuid: UUID) {
        inkHp[uuid] = maxInkHp
        syncHealthBar(uuid)
    }

    fun damageInkHp(uuid: UUID, amount: Int): Int {
        val cur = getInkHp(uuid)
        val next = (cur - amount).coerceIn(0, maxInkHp)
        inkHp[uuid] = next
        inkLastDamageAt[uuid] = System.currentTimeMillis()
        inkRegenCarry[uuid] = 0.0
        syncHealthBar(uuid)
        return next
    }

    private fun giveSplatGuns() {
        val item = ItemStack(Material.CROSSBOW, 1)
        val meta = item.itemMeta
        meta.displayName(Component.text("Сплат-пушка").color(TextColor.color(0xFF55FF)))
        meta.persistentDataContainer.set(
            NamespacedKey(SplatoonPlugin.instance, "splatGun"),
            PersistentDataType.BOOLEAN,
            true
        )
        item.itemMeta = meta

        commands.keys.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid) ?: return@forEach
            p.inventory.addItem(item.clone())
        }
    }

    private fun ensureBowAmmo(player: Player) {
        val ammoKey = NamespacedKey(SplatoonPlugin.instance, "splatAmmo")
        val inv = player.inventory

        // Если уже есть наш патрон — просто обновим цвет.
        var ammoSlot = -1
        for (i in 0 until inv.size) {
            val it = inv.getItem(i) ?: continue
            if (it.type == Material.AIR || !it.hasItemMeta()) continue
            if (it.itemMeta.persistentDataContainer.has(ammoKey, PersistentDataType.BOOLEAN)) {
                ammoSlot = i
                break
            }
        }

        val baseTeam = commands[player.uniqueId] ?: 0
        val paintTeam = getAmmoTeam(player.uniqueId) ?: baseTeam
        val colorName = when (paintTeam) {
            0 -> "Red"
            3 -> "Blue"
            2 -> "Green"
            1 -> "Yellow"
            else -> "Red"
        }

        val item = if (ammoSlot >= 0) {
            inv.getItem(ammoSlot) ?: return
        } else {
            val created = ItemStack(Material.ARROW, 1)
            val m = created.itemMeta
            m.persistentDataContainer.set(ammoKey, PersistentDataType.BOOLEAN, true)
            created.itemMeta = m

            val slot = firstEmptyMainInvSlot(inv) ?: 35
            inv.setItem(slot, created)
            ammoSlot = slot
            created
        }

        item.setData(DataComponentTypes.CUSTOM_NAME, Component.text(colorName))
        inv.setItem(ammoSlot, item)
    }

    private fun firstEmptyMainInvSlot(inv: org.bukkit.inventory.PlayerInventory): Int? {
        for (i in 9..35) {
            val it = inv.getItem(i)
            if (it == null || it.type == Material.AIR) return i
        }
        return null
    }

    private fun playSoundToAllPlayers(sound: Sound) {
        commands.keys.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.playSound(sound)
        }
    }

    private fun showTitleToAllPlayers(title: Component, subtitle: Component) {
        val titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
        )
        commands.keys.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.showTitle(titleObj)
        }
    }

    private fun showTitleToWorldPlayers(title: Component, subtitle: Component) {
        val w = Bukkit.getWorld(this.worldName)
        val targets = (w?.players ?: emptyList()).toMutableList()
        commands.keys.forEach { id ->
            val p = Bukkit.getPlayer(id)
            if (p != null && !targets.contains(p)) targets.add(p)
        }
        val titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
        )
        targets.forEach { it.showTitle(titleObj) }
    }

    private fun showWinnerAnnouncement(winner: Int) {
        showTitleToAllPlayers(
            when (winner) {
                0 -> Component.text("КРАСНЫЕ ПОБЕДИЛИ!", NamedTextColor.RED)
                3 -> Component.text("СИНИЕ ПОБЕДИЛИ!", NamedTextColor.BLUE)
                1 -> Component.text("ЖЕЛТЫЕ ПОБЕДИЛИ!", NamedTextColor.YELLOW)
                2 -> Component.text("ЗЕЛЕНЫЕ ПОБЕДИЛИ!", NamedTextColor.GREEN)
                else -> Component.text("НИЧЬЯ!", NamedTextColor.GOLD)
            },
            Component.text(
                "Возвращение в лобби через ${SplatoonSettings.returnToLobbyDelaySeconds} секунд...",
                NamedTextColor.GRAY
            )
        )
    }

    private fun startBoostTimer() {
        if (!SplatoonSettings.boostsEnabled) return
        scheduleNextBoost(0L)
    }

    private fun scheduleNextBoost(delayTicks: Long) {
        boostTimerTask?.cancel()

        boostTimerTask = Bukkit.getScheduler().runTaskLater(SplatoonPlugin.instance, Runnable {
            val w = Bukkit.getWorld(worldName) ?: return@Runnable

            if (Random.nextInt(100) < SplatoonSettings.boostsBombPercent) {
                giveSplatBomb(w)
            } else {
                giveBacillus(w)
            }

            val minSec = SplatoonSettings.boostsMinIntervalSeconds
            val maxSec = SplatoonSettings.boostsMaxIntervalSeconds
            val nextSec = if (maxSec <= minSec) minSec else Random.nextInt(minSec, maxSec + 1)
            scheduleNextBoost(nextSec * 20L)
        }, delayTicks)
    }

    private fun startMainTimer(worldName: String) {
        timeLeft = totalTime

        val w = Bukkit.getWorld(worldName)
        if (w != null) recalcPaintableBlocks(w)

        clearScoreboards()
        createPlayerScoreboards()

        removeBossBar()
        createBossBar()

        gameTimerTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            updateBossBar()

            if (timeLeft <= 0) {
                endGame(worldName)
                return@Runnable
            }

            when (timeLeft) {
                60 -> {
                    showTitleToAllPlayers(
                        Component.text("Осталась 1 минута!", NamedTextColor.YELLOW),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 1.0f, 1.0f)
                    )
                }

                30 -> {
                    showTitleToAllPlayers(
                        Component.text("30 секунд!", NamedTextColor.GOLD),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 1.0f, 1.2f)
                    )
                }

                3, 2, 1 -> {
                    showTitleToAllPlayers(
                        Component.text("$timeLeft", NamedTextColor.RED),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.pling"),
                            Sound.Source.MASTER,
                            1.0f,
                            (1.0f + (10 - timeLeft) * 0.1f)
                        )
                    )
                }
            }

            timeLeft--
        }, 0L, 20L)

        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            updateAllPlayerScoreboards()
        }, 0L, SplatoonSettings.scoreboardUpdateTicks)
    }

    fun recalcPaintableBlocks(world: World) {
        totalPaintableBlocks = 0
        paintedCommand.keys.forEach { paintedCommand[it] = 0 }

        val materialToTeam = mutableMapOf<Material, Int>()
        commandColors.forEach { (team, mat) -> materialToTeam[mat] = team }

        val paintable = setOf(
            Material.WHITE_CONCRETE,
            Material.RED_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.BLUE_CONCRETE
        )

        world.loadedChunks.forEach { chunk ->
            for (x in 0..15) {
                for (z in 0..15) {
                    for (y in world.minHeight until world.maxHeight) {
                        val b = chunk.getBlock(x, y, z)
                        if (!paintable.contains(b.type)) continue
                        totalPaintableBlocks++
                        val team = materialToTeam[b.type]
                        if (team != null) paintedCommand[team] = (paintedCommand[team] ?: 0) + 1
                    }
                }
            }
        }
    }

    fun applyAmmoOverride(uuid: UUID, team: Int, durationMs: Long) {
        ammoOverride[uuid] = team to (System.currentTimeMillis() + durationMs)
    }

    fun getAmmoTeam(uuid: UUID): Int? {
        val base = commands[uuid] ?: return null
        val override = ammoOverride[uuid]
        if (override != null) {
            if (System.currentTimeMillis() < override.second) return override.first
            ammoOverride.remove(uuid)
        }
        return base
    }


    fun setSpawnProtection(player: Player, durationMs: Long) {
        val uuid = player.uniqueId
        if (durationMs <= 0) {
            clearSpawnProtection(uuid, player)
            updateSpawnGlow(player, false)
            return
        }
        spawnProtectedUntil[uuid] = System.currentTimeMillis() + durationMs
        spawnProtectedOrigin[uuid] = player.location.toVector()
        spawnProtectionMoved[uuid] = false

        // Защищаем игрока от толканий/коллизий другими игроками, пока активен spawn protection.
        player.isCollidable = false

        updateSpawnGlow(player, true)
    }

    private fun clearSpawnProtection(uuid: UUID, player: Player? = null) {
        spawnProtectedUntil.remove(uuid)
        spawnProtectedOrigin.remove(uuid)
        spawnProtectionMoved.remove(uuid)
        if (player != null) {
            player.isCollidable = true
        }
    }

    fun isSpawnSafe(player: Player): Boolean {
        val uuid = player.uniqueId
        val until = spawnProtectedUntil[uuid] ?: return false

        val now = System.currentTimeMillis()
        if (now < until) return true

        val moved = spawnProtectionMoved[uuid] ?: return false
        if (moved) return false

        val origin = spawnProtectedOrigin[uuid] ?: return false
        val cur = player.location.toVector()
        if (hasMoved(cur, origin)) {
            spawnProtectionMoved[uuid] = true
            clearSpawnProtection(uuid, player)
            return false
        }
        return true
    }

    fun onPlayerMoved(player: Player) {
        val uuid = player.uniqueId
        val until = spawnProtectedUntil[uuid] ?: return
        val origin = spawnProtectedOrigin[uuid] ?: return

        val moved = spawnProtectionMoved[uuid] ?: return
        if (moved) return

        val cur = player.location.toVector()
        if (hasMoved(cur, origin)) {
            spawnProtectionMoved[uuid] = true
            if (System.currentTimeMillis() >= until) {
                clearSpawnProtection(uuid, player)
            }
        }
    }

    private fun updateSpawnProtectionMovement(player: Player) {
        val uuid = player.uniqueId
        val until = spawnProtectedUntil[uuid] ?: return
        val origin = spawnProtectedOrigin[uuid] ?: return
        val moved = spawnProtectionMoved[uuid] ?: return

        val now = System.currentTimeMillis()

        if (!moved) {
            val cur = player.location.toVector()
            if (hasMoved(cur, origin)) {
                spawnProtectionMoved[uuid] = true
                if (now >= until) {
                    clearSpawnProtection(uuid, player)
                }
            }
            return
        }

        if (now >= until) {
            clearSpawnProtection(uuid, player)
        }
    }

    private fun hasMoved(cur: Vector, origin: Vector): Boolean {
        // Не считаем мелкие сдвиги (толкания/погрешности позиции) как "движение со спавна".
        // Сбрасываем спавн-протекшн только когда игрок реально вышел из исходного блока.
        if (cur.blockX != origin.blockX || cur.blockZ != origin.blockZ) return true
        return abs(cur.blockY - origin.blockY) >= 1
    }

    fun teleportToTeamSpawn(player: Player) {
        val w = Bukkit.getWorld(worldName) ?: return
        val team = commands[player.uniqueId]
        val loc = pickTeamSpawnLocation(team, w) ?: w.spawnLocation
        player.teleport(loc)
    }

    private fun pickTeamSpawnLocation(team: Int?, world: World): org.bukkit.Location? {
        if (team == null) return null
        val points = teamSpawns[team] ?: return null
        if (points.isEmpty()) return null

        val chosen = points[Random.nextInt(points.size)]
        val fallback = world.spawnLocation
        val yaw = chosen.yaw ?: fallback.yaw
        val pitch = chosen.pitch ?: fallback.pitch
        return org.bukkit.Location(world, chosen.x, chosen.y, chosen.z, yaw, pitch)
    }

    private fun createBossBar() {
        val title = formatBossBarTitle()
        bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID)
        bossBar?.isVisible = true
        commands.keys.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) bossBar?.addPlayer(player)
        }
        updateBossBar()
    }

    private fun removeBossBar() {
        bossBar?.removeAll()
        bossBar = null
    }

    private fun formatBossBarTitle(): String {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeString = "$minutes:${"%02d".format(seconds)}"
        val prefix = when {
            timeLeft <= 30 -> "§c"
            timeLeft <= 60 -> "§e"
            else -> "§a"
        }
        return "${prefix}До конца: §f$timeString"
    }

    private fun updateBossBar() {
        val bar = bossBar ?: return
        val progress = (timeLeft.toDouble() / totalTime.toDouble()).coerceIn(0.0, 1.0)
        bar.progress = progress
        bar.setTitle(formatBossBarTitle())
        bar.color = when {
            timeLeft <= 30 -> BarColor.RED
            timeLeft <= 60 -> BarColor.YELLOW
            else -> BarColor.GREEN
        }
    }

    private fun clearScoreboards() {
        playerObjectives.clear()
        playerScoreboards.clear()
    }

    private fun createPlayerScoreboards() {
        commands.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            val sb = Bukkit.getScoreboardManager().newScoreboard
            val obj = sb.registerNewObjective(
                "gametimer",
                org.bukkit.scoreboard.Criteria.DUMMY,
                Component.text("Splatoon", NamedTextColor.GOLD)
            )
            obj.displaySlot = DisplaySlot.SIDEBAR
            player.scoreboard = sb
            playerScoreboards[uuid] = sb
            playerObjectives[uuid] = obj
        }
        updateAllPlayerScoreboards()
    }

    private fun updateSpawnNameTags() {
        val protectedNames = mutableSetOf<String>()
        commands.keys.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid) ?: return@forEach
            if (isSpawnSafe(p)) protectedNames.add(p.name)
        }

        commands.keys.forEach { viewerUuid ->
            val sb = playerScoreboards[viewerUuid] ?: return@forEach
            val team = sb.getTeam("sp_spawn") ?: sb.registerNewTeam("sp_spawn").apply {
                setPrefix("§aSPAWN §r")
                setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
            }

            val toRemove = team.entries.filter { it !in protectedNames }
            toRemove.forEach { team.removeEntry(it) }

            protectedNames.forEach { name ->
                if (!team.hasEntry(name)) team.addEntry(name)
            }
        }
    }

    private fun updateAllPlayerScoreboards() {
        updateSpawnNameTags()

        val totalForPercent = if (totalPaintableBlocks > 0) {
            totalPaintableBlocks
        } else {
            activeTeams.sumOf { paintedCommand[it] ?: 0 }
        }

        commands.keys.forEach { uuid ->
            val sb = playerScoreboards[uuid] ?: return@forEach
            val obj = playerObjectives[uuid] ?: return@forEach

            sb.entries.forEach { entry -> sb.resetScores(entry) }

            var score = 15
            obj.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").score = score
            score--

            obj.getScore("§f§lСЧЕТ:").score = score
            score--

            activeTeams.forEach { team ->
                if (score <= 0) return@forEach
                obj.getScore(formatTeamLine(team, totalForPercent)).score = score
                score--
            }

            if (score <= 0) return@forEach
            obj.getScore(" ").score = score
            score--

            val team = commands[uuid]
            if (score <= 0) return@forEach
            obj.getScore("§f§lВы: §f${teamLabel(team)}").score = score
            score--

            if (score <= 0) return@forEach
            obj.getScore(formatAmmoLine(uuid)).score = score
            score--

            if (score <= 0) return@forEach
            obj.getScore("  ").score = score
            score--

            if (score <= 0) return@forEach
            obj.getScore("§f§lВКЛАД:").score = score
            score--

            if (team != null) {
                val teamPlayers = commands.entries.filter { it.value == team }.map { it.key }
                val teamTotal = teamPlayers.sumOf { (paintedPerson[it] ?: 0).coerceAtLeast(0) }

                val sorted = teamPlayers
                    .map { it to (paintedPerson[it] ?: 0) }
                    .sortedByDescending { it.second }

                sorted.forEach { (pid, value) ->
                    if (score <= 0) return@forEach
                    obj.getScore(formatPlayerContributionLine(pid, value, teamTotal)).score = score
                    score--
                }
            }

            obj.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬§6").score = 0
        }
    }

    private fun teamLabel(team: Int?): String {
        return when (team) {
            0 -> "§cКрасные"
            1 -> "§eЖёлтые"
            2 -> "§aЗелёные"
            3 -> "§9Синие"
            else -> "§7-"
        }
    }

    private fun formatAmmoLine(uuid: UUID): String {
        val ammoTeam = getAmmoTeam(uuid)
        val baseTeam = commands[uuid]
        val prefix = if (ammoTeam != null && baseTeam != null && ammoTeam != baseTeam) "§d" else "§f"
        return "${prefix}Патроны: §f${teamLabel(ammoTeam)}"
    }

    private fun formatTeamLine(team: Int, totalPaintable: Int): String {
        val value = paintedCommand[team] ?: 0
        val percent = if (totalPaintable <= 0) 0 else ((value.toDouble() * 100.0) / totalPaintable.toDouble()).roundToInt()
        return when (team) {
            0 -> "§cКрасная: §f$value §7(${percent}%)"
            1 -> "§eЖелтая: §f$value §7(${percent}%)"
            2 -> "§aЗеленая: §f$value §7(${percent}%)"
            3 -> "§9Синяя: §f$value §7(${percent}%)"
            else -> "§fКоманда: §f$value §7(${percent}%)"
        }
    }

    private fun formatPlayerContributionLine(uuid: UUID, value: Int, teamTotal: Int): String {
        val nameRaw = Bukkit.getOfflinePlayer(uuid).name ?: "Player"
        val name = if (nameRaw.length > 10) nameRaw.substring(0, 10) else nameRaw
        val percent = if (teamTotal <= 0) 0 else (((value.coerceAtLeast(0)).toDouble() * 100.0) / teamTotal.toDouble()).roundToInt()
        val k = kills[uuid] ?: 0
        return "§b$name: §f$value §7(${percent}%) §c✦$k"
    }

    private fun ensureInkHealth(player: Player) {
        val attr = player.getAttribute(Attribute.MAX_HEALTH) ?: return
        val desired = (maxInkHp * 2).toDouble().coerceAtLeast(2.0)
        if (attr.baseValue != desired) {
            attr.baseValue = desired
        }
    }

    fun syncHealthBar(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid) ?: return
        syncHealthBar(player)
    }

    fun syncHealthBar(player: Player) {
        ensureInkHealth(player)
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0
        val hp = getInkHp(player.uniqueId)
        val desiredHealth = (hp * 2).toDouble().coerceIn(1.0, maxHealth)
        if (player.absorptionAmount != 0.0) {
            player.absorptionAmount = 0.0
        }
        if (player.health != desiredHealth) {
            player.health = desiredHealth
        }
    }

    private fun restoreVanillaHealth(player: Player) {
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.absorptionAmount = 0.0
        player.isCollidable = true
        player.removePotionEffect(PotionEffectType.GLOWING)
    }

    private fun updateSpawnGlow(player: Player, enabled: Boolean) {
        if (!enabled) {
            player.removePotionEffect(PotionEffectType.GLOWING)
            return
        }
        player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false))
    }
}

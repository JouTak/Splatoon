package ru.joutak.splatoon.scripts

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getPlayer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.DisplaySlot
import ru.joutak.minigames.domain.GameResult
import ru.joutak.minigames.domain.Player
import ru.joutak.minigames.storage.GameResultStorage
import ru.joutak.splatoon.SplatoonPlugin
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

class Game(var worldName: String) {
    val paintedCommand: MutableMap<Int, Int> = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    var paintedPerson: MutableMap<UUID, Int> = mutableMapOf()
    val commandColors: Map<Int, Material> = mapOf(
        0 to Material.RED_CONCRETE,
        3 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        1 to Material.YELLOW_CONCRETE
    )
    var commands: MutableMap<UUID, Int> = mutableMapOf()

    private var countdownTask: BukkitTask? = null
    private var gameTimerTask: BukkitTask? = null
    private var boostTimerTask: BukkitTask? = null
    private var scoreboardUpdateTask: BukkitTask? = null

    private var timeLeft = 0
    private val totalTime = 5 * 60

    private var bossBar: BossBar? = null
    private var activeTeams: List<Int> = listOf()

    private val playerScoreboards: MutableMap<UUID, org.bukkit.scoreboard.Scoreboard> = mutableMapOf()
    private val playerObjectives: MutableMap<UUID, org.bukkit.scoreboard.Objective> = mutableMapOf()

    fun shutdownGame() {
        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
        boostTimerTask?.cancel()

        removeBossBar()
        clearScoreboards()

        val emptyScoreboard = Bukkit.getScoreboardManager().newScoreboard
        val lobbyWorld = Bukkit.getWorld(SplatoonPlugin.instance.lobbyName)
        val spawn = lobbyWorld?.spawnLocation ?: Bukkit.getWorlds()[0].spawnLocation

        commands.keys.forEach { playerId ->
            val player = getPlayer(playerId)
            if (player != null) {
                player.scoreboard = emptyScoreboard
                player.inventory.clear()
                player.health = 20.0
                player.foodLevel = 20
                player.saturation = 20f
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }
                player.teleport(spawn)
            }
        }
    }

    fun startGame(worldName: String) {
        activeTeams = commands.values.toSet().sorted()

        commands.keys.forEach { uuid ->
            val player = getPlayer(uuid)
            if (player != null) {
                player.inventory.clear()
                player.teleport(Bukkit.getWorld(this.worldName)!!.spawnLocation)
            }
        }
        startCountdown(worldName)
    }

    fun endGame(worldName: String) {
        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
        boostTimerTask?.cancel()

        removeBossBar()
        clearScoreboards()

        val winner = determineWinner()
        showWinnerAnnouncement(winner)
        playSoundToAllPlayers(
            Sound.sound(
                Key.key("ui.toast.challenge_complete"),
                Sound.Source.MASTER,
                1.0f,
                1.0f
            )
        )
        val emptyScoreboard = Bukkit.getScoreboardManager().newScoreboard

        val participantsList = mutableListOf<Player>()
        val winnersList = mutableListOf<Player>()

        commands.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                val participant = Player(
                    name = player.name
                )
                participantsList.add(participant)

                if (commands[uuid] == winner) {
                    winnersList.add(participant)
                }
            }
        }
        val result = GameResult(
            gameUuid = UUID.randomUUID(),
            gameName = "Splatoon",
            participants = participantsList,
            winners = winnersList,
            dateTime = LocalDateTime.now(),
            results = paintedPerson.toMap()
        )
        GameResultStorage.save(result)

        Bukkit.getScheduler().runTaskLater(SplatoonPlugin.instance, Runnable {
            val lobbyLocation = Bukkit.getWorld(SplatoonPlugin.instance.lobbyName)

            if (lobbyLocation == null) {
                SplatoonPlugin.instance.logger.severe("Не удалось найти мир лобби: ${SplatoonPlugin.instance.lobbyName}. Удаление игры остановлено.")
                GameManager.deleteGame(this.worldName, this)
                return@Runnable
            }

            commands.keys.forEach { playerId ->
                val player = getPlayer(playerId)

                if (player != null) {
                    player.scoreboard = emptyScoreboard
                    player.inventory.clear()
                    player.health = 20.0
                    player.foodLevel = 20
                    player.saturation = 20f
                    player.activePotionEffects.forEach { effect ->
                        player.removePotionEffect(effect.type)
                    }
                    player.inventory.clear()
                    player.teleport(lobbyLocation.spawnLocation)
                }
            }

            GameManager.deleteGame(this.worldName, this)
        }, 100L)
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
                    commands.keys.forEach { playerId ->
                        val colors = mapOf(
                            0 to Component.text("Ваша команда: Красные!", NamedTextColor.RED),
                            3 to Component.text("Ваша команда: Синие!", NamedTextColor.BLUE),
                            2 to Component.text("Ваша команда: Зелёные!", NamedTextColor.GREEN),
                            1 to Component.text("Ваша команда: Жёлтые!", NamedTextColor.YELLOW)
                        )
                        val titleObj = Title.title(
                            Component.text("ПОДГОТОВКА!", NamedTextColor.BLACK),
                            colors[commands[playerId]] ?: Component.empty(),
                            Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500)
                            )
                        )

                        getPlayer(playerId)?.showTitle(titleObj)
                    }
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.pling"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f
                        )
                    )
                }

                3 -> {
                    showTitleToAllPlayers(
                        Component.text("3", NamedTextColor.YELLOW),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.pling"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f
                        )
                    )
                }

                2 -> {
                    showTitleToAllPlayers(
                        Component.text("2", NamedTextColor.GOLD),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.pling"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.2f
                        )
                    )
                }

                1 -> {
                    showTitleToAllPlayers(
                        Component.text("1", NamedTextColor.RED),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.pling"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.4f
                        )
                    )
                }

                0 -> {
                    showTitleToAllPlayers(
                        Component.text("СТАРТ!", NamedTextColor.GREEN),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("entity.player.levelup"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f
                        )
                    )
                    giveSplatGuns()

                    startMainTimer(worldName)
                    startBoostTimer()
                    countdownTask?.cancel()
                }
            }
            countdown--
        }, 0L, 20L)
    }

    private fun giveSplatGuns() {
        val item = ItemStack(Material.GOLDEN_SHOVEL, 1)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Сплат-пушка").color(TextColor.color(0xFF55FF))
        )
        meta.persistentDataContainer.set(
            NamespacedKey(SplatoonPlugin.instance, "splatGun"), PersistentDataType.BOOLEAN, true
        )
        item.itemMeta = meta

        commands.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.inventory?.addItem(item.clone())
        }
    }

    private fun playSoundToAllPlayers(sound: Sound) {
        commands.keys.forEach { playerId ->
            getPlayer(playerId)?.playSound(sound)
        }
    }

    private fun showTitleToAllPlayers(title: Component, subtitle: Component) {
        val titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(2000),
                Duration.ofMillis(500)
            )
        )

        commands.keys.forEach { playerId ->
            getPlayer(playerId)?.showTitle(titleObj)
        }
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
            Component.text("Возвращение в лобби через 5 секунд...", NamedTextColor.GRAY)
        )
    }

    private fun startBoostTimer() {
        boostTimerTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            giveSplatBomb(Bukkit.getWorld(worldName)!!)
        }, 0L, 20L * 20 + Random.nextInt(21 * 20))
    }

    private fun startMainTimer(worldName: String) {
        timeLeft = totalTime

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
                        Sound.sound(
                            Key.key("block.note_block.bell"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f
                        )
                    )
                }

                30 -> {
                    showTitleToAllPlayers(
                        Component.text("30 секунд!", NamedTextColor.GOLD),
                        Component.empty()
                    )
                    playSoundToAllPlayers(
                        Sound.sound(
                            Key.key("block.note_block.bell"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.2f
                        )
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
        }, 0L, 10L)
    }

    private fun createBossBar() {
        val title = formatBossBarTitle()
        bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID)
        bossBar?.isVisible = true
        commands.keys.forEach { playerId ->
            val player = getPlayer(playerId)
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
            val player = getPlayer(uuid) ?: return@forEach

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

    private fun updateAllPlayerScoreboards() {
        val totalPainted = activeTeams.sumOf { paintedCommand[it] ?: 0 }

        commands.keys.forEach { uuid ->
            val sb = playerScoreboards[uuid] ?: return@forEach
            val obj = playerObjectives[uuid] ?: return@forEach

            sb.entries.forEach { entry ->
                sb.resetScores(entry)
            }

            var score = 15

            val top = "§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            obj.getScore(top).score = score
            score--

            val blank1 = " "
            obj.getScore(blank1).score = score
            score--

            obj.getScore("§f§lСЧЕТ КОМАНД:").score = score
            score--

            activeTeams.forEach { team ->
                obj.getScore(formatTeamLine(team, totalPainted)).score = score
                score--
            }

            val blank2 = "  "
            obj.getScore(blank2).score = score
            score--

            val team = commands[uuid]
            obj.getScore(formatYouLine(team)).score = score
            score--

            val blank3 = "   "
            obj.getScore(blank3).score = score
            score--

            obj.getScore("§f§lВКЛАД КОМАНДЫ:").score = score
            score--

            if (team != null) {
                val teamPlayers = commands.entries
                    .filter { it.value == team }
                    .map { it.key }

                val teamTotal = teamPlayers.sumOf { paintedPerson[it] ?: 0 }

                val sorted = teamPlayers
                    .map { it to (paintedPerson[it] ?: 0) }
                    .sortedByDescending { it.second }

                sorted.forEach { (pid, value) ->
                    if (score <= 1) return@forEach
                    obj.getScore(formatPlayerContributionLine(pid, value, teamTotal)).score = score
                    score--
                }
            }

            val bottom = "§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬§6"
            obj.getScore(bottom).score = 1
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

    private fun formatYouLine(team: Int?): String {
        return "§f§lВы: §f${teamLabel(team)}"
    }

    private fun formatTeamLine(team: Int, totalPainted: Int): String {
        val value = paintedCommand[team] ?: 0
        val percent = if (totalPainted <= 0) 0 else ((value.toDouble() * 100.0) / totalPainted.toDouble()).roundToInt()

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
        val percent = if (teamTotal <= 0) 0 else ((value.toDouble() * 100.0) / teamTotal.toDouble()).roundToInt()
        return "§b$name: §f$value §7(${percent}%)"
    }
}

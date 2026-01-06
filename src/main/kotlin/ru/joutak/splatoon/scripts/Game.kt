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
import kotlin.collections.forEach
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

    private var gameScoreboard: org.bukkit.scoreboard.Scoreboard? = null
    private var objective: org.bukkit.scoreboard.Objective? = null

    private var bossBar: BossBar? = null
    private var activeTeams: List<Int> = listOf()

    fun shutdownGame() {
        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
        boostTimerTask?.cancel()

        removeBossBar()

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
        createTimerScoreboard()
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
            updateTimerScoreboard()
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

    private fun createTimerScoreboard() {
        gameScoreboard = Bukkit.getScoreboardManager().newScoreboard

        objective = gameScoreboard?.registerNewObjective(
            "gametimer",
            org.bukkit.scoreboard.Criteria.DUMMY,
            Component.text("Splatoon", NamedTextColor.GOLD)
        )

        objective?.displaySlot = DisplaySlot.SIDEBAR

        commands.keys.forEach { playerId ->
            getPlayer(playerId)?.scoreboard = gameScoreboard!!
        }

        updateTimerScoreboard()
    }

    private fun updateTimerScoreboard() {
        gameScoreboard?.entries?.forEach { entry ->
            gameScoreboard?.resetScores(entry)
        }

        var score = 15

        val top = "§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        objective?.getScore(top)?.score = score
        score--

        val l1 = " "
        objective?.getScore(l1)?.score = score
        score--

        objective?.getScore("§f§lСЧЕТ КОМАНД:")?.score = score
        score--

        activeTeams.forEach { team ->
            objective?.getScore(formatTeamLine(team))?.score = score
            score--
        }

        val bottom = "§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬§6"
        objective?.getScore(bottom)?.score = 0
    }

    private fun formatTeamLine(team: Int): String {
        return when (team) {
            0 -> "§cКрасная: §f${paintedCommand[0]}"
            1 -> "§eЖелтая: §f${paintedCommand[1]}"
            2 -> "§aЗеленая: §f${paintedCommand[2]}"
            3 -> "§9Синяя: §f${paintedCommand[3]}"
            else -> "§fКоманда: §f${paintedCommand[team] ?: 0}"
        }
    }
}

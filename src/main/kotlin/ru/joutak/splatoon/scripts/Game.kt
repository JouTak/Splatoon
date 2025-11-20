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
        1 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        3 to Material.YELLOW_CONCRETE
    )
    var commands: MutableMap<UUID, Int> = mutableMapOf()

    private var countdownTask: BukkitTask? = null
    private var gameTimerTask: BukkitTask? = null
    private var boostTimerTask: BukkitTask? = null
    private var scoreboardUpdateTask: BukkitTask? = null

    private var timeLeft = 0
    private var gameScoreboard: org.bukkit.scoreboard.Scoreboard? = null
    private var objective: org.bukkit.scoreboard.Objective? = null
    fun startGame(worldName: String) {
        commands.keys.forEach { uuid ->
            val player =  getPlayer(uuid)!!
            player.inventory.clear()
            player.teleport(Bukkit.getWorld(this.worldName)!!.spawnLocation)
        }
        startCountdown(worldName)


    }

    fun endGame(worldName: String) {
        gameTimerTask?.cancel()
        countdownTask?.cancel()
        scoreboardUpdateTask?.cancel()
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
            gameName = worldName,
            participants = participantsList,
            winners = winnersList,
            dateTime = LocalDateTime.now(),
            results = paintedPerson.toMap()
        )
        GameResultStorage.save(result)
        Bukkit.getScheduler().runTaskLater(SplatoonPlugin.instance, Runnable {
            commands.keys.forEach { playerId ->
                val lobbyLocation = Bukkit.getWorld(SplatoonPlugin.instance.lobbyName)
                getPlayer(playerId)!!.scoreboard = emptyScoreboard
                val player = getPlayer(playerId)!!
                player.inventory.clear()
                player.health = 20.0
                player.foodLevel = 20
                player.saturation = 20f
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }
                player.inventory.clear()
                player.teleport(lobbyLocation!!.spawnLocation)
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
                            1 to Component.text("Ваша команда: Синие!", NamedTextColor.BLUE),
                            2 to Component.text("Ваша команда: Зелёные!", NamedTextColor.GREEN),
                            3 to Component.text("Ваша команда: Жёлтые!", NamedTextColor.YELLOW)
                        )
                        val titleObj = Title.title(
                            Component.text("ПОДГОТОВКА!", NamedTextColor.BLACK),
                            colors[commands[playerId]] as Component,
                            Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500)
                            )
                        )

                        getPlayer(playerId)!!.showTitle(titleObj)
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
        }, 0L, 20L) // 20 тиков = 1 секунда
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
            Bukkit.getPlayer(uuid)?.inventory?.addItem(item)
        }
    }

    private fun playSoundToAllPlayers(sound: Sound) {
        commands.keys.forEach { playerId ->
            getPlayer(playerId)!!.playSound(sound)
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
            getPlayer(playerId)!!.showTitle(titleObj)
        }
    }

    private fun showWinnerAnnouncement(winner: Int) {
        showTitleToAllPlayers(
            when (winner) {
                0 -> Component.text("КРАСНЫЕ ПОБЕДИЛИ!", NamedTextColor.RED)
                1 -> Component.text("СИНИЕ ПОБЕДИЛИ!", NamedTextColor.BLUE)
                3 -> Component.text("ЖЕЛТЫЕ ПОБЕДИЛИ!", NamedTextColor.YELLOW)
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

        timeLeft = 5 * 60
        createTimerScoreboard()
        gameTimerTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            if (timeLeft <= 0) {
                endGame(worldName)
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

    private fun createTimerScoreboard() {
        gameScoreboard = Bukkit.getScoreboardManager().newScoreboard

        objective = gameScoreboard?.registerNewObjective(
            "gametimer",
            org.bukkit.scoreboard.Criteria.DUMMY,
            Component.text("Splatoon", NamedTextColor.GOLD)
        )

        objective?.displaySlot = DisplaySlot.SIDEBAR

        commands.keys.forEach { playerId ->
            getPlayer(playerId)!!.scoreboard = gameScoreboard!!
        }

        updateTimerScoreboard()
    }

    private fun updateTimerScoreboard() {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeString = "$minutes:${"%02d".format(seconds)}"

        val timeColor = when {
            timeLeft <= 30 -> NamedTextColor.RED
            timeLeft <= 60 -> NamedTextColor.YELLOW
            else -> NamedTextColor.GREEN
        }

        gameScoreboard?.entries?.forEach { entry ->
            gameScoreboard?.resetScores(entry)
        }
        val colorCodes = mapOf(
            NamedTextColor.RED to "§c",
            NamedTextColor.YELLOW to "§e",
            NamedTextColor.GREEN to "§a",
            NamedTextColor.GOLD to "§6",
            NamedTextColor.WHITE to "§f",
            NamedTextColor.GRAY to "§7",
            NamedTextColor.AQUA to "§b"
        )

        val timeColorCode = colorCodes[timeColor] ?: "§f"

        objective?.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")?.score = 12
        objective?.getScore("")?.score = 11
        objective?.getScore("§f§lСЧЕТ КОМАНД:")?.score = 10
        objective?.getScore("§cКрасная: §f${paintedCommand[0]}")?.score = 9
        objective?.getScore("§9Синяя: §f${paintedCommand[1]}")?.score = 8
        objective?.getScore("§eЖелтая: §f${paintedCommand[3]}")?.score = 7
        objective?.getScore("§aЗеленая: §f${paintedCommand[2]}")?.score = 6
        objective?.getScore("  ")?.score = 5
        objective?.getScore("§fОсталось времени:")?.score = 4
        objective?.getScore("$timeColorCode$timeString")?.score = 3
        objective?.getScore("   ")?.score = 2
        objective?.getScore("§7   ")?.score = 1
        objective?.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")?.score = 0
    }
}
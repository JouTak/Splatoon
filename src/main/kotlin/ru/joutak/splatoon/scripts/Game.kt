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
import ru.joutak.splatoon.SplatoonPlugin
import java.time.Duration
import java.util.UUID
import kotlin.collections.forEach

class Game(var worldName: String) {
    val paintedCommand: MutableMap<Int, Int> = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    var paintedPerson: MutableMap<UUID, Int> = mutableMapOf()
    val commandColors: Map<Int, Material> = mapOf(
        0 to Material.RED_CONCRETE,
        1 to Material.BLUE_CONCRETE,
        2 to Material.GREEN_CONCRETE,
        3 to Material.YELLOW_CONCRETE
    )
    var commands: Map<UUID, Int> = mutableMapOf()

    private var countdownTask: BukkitTask? = null
    private var gameTimerTask: BukkitTask? = null

    private var scoreboardUpdateTask: BukkitTask? = null
    private var isGameRunning = false
    private var timeLeft = 0
    private var gameScoreboard: org.bukkit.scoreboard.Scoreboard? = null
    private var objective: org.bukkit.scoreboard.Objective? = null
    fun startGame() {
        commands.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.teleport(Bukkit.getWorld(worldName)!!.spawnLocation)
        }

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
        startCountdown()


    }
    fun endGame(){

    }

    private fun startCountdown() {
        var countdown = 10

        countdownTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            when (countdown) {
                10 -> {
                    showTitleToAllPlayers(
                        Component.text("ПОДГОТОВКА!", NamedTextColor.BLACK),
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
                    startMainTimer()
                    countdownTask?.cancel()
                }
            }
            countdown--
        }, 0L, 20L) // 20 тиков = 1 секунда
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

    private fun startMainTimer() {
        timeLeft = 5 * 60
        createTimerScoreboard()
        gameTimerTask = Bukkit.getScheduler().runTaskTimer(SplatoonPlugin.instance, Runnable {
            if (timeLeft <= 0) {
                endGame()
                return@Runnable
            }

            // Показываем важные уведомления в центре
            when (timeLeft) {
                60 -> {
                    showTitleToAllPlayers(
                        Component.text("Осталась 1 минута!", NamedTextColor.YELLOW),
                        Component.empty()
                    )
                    playSoundToAllPlayers(Sound.sound(
                        Key.key("block.note_block.bell"),
                        Sound.Source.MASTER,
                        1.0f,
                        1.0f
                    ))
                }
                30 -> {
                    showTitleToAllPlayers(
                        Component.text("30 секунд!", NamedTextColor.GOLD),
                        Component.empty()
                    )
                    playSoundToAllPlayers(Sound.sound(
                        Key.key("block.note_block.bell"),
                        Sound.Source.MASTER,
                        1.0f,
                        1.2f
                    ))
                }
                10, 9, 8, 7, 6, 5, 4, 3, 2, 1 -> {
                    showTitleToAllPlayers(
                        Component.text("$timeLeft", NamedTextColor.RED),
                        Component.empty()
                    )
                    playSoundToAllPlayers(Sound.sound(
                        Key.key("block.note_block.pling"),
                        Sound.Source.MASTER,
                        1.0f,
                        (1.0f + (10 - timeLeft) * 0.1f)
                    ))
                }
            }

            timeLeft--
            updateTimerScoreboard()
        }, 0L, 20L)
    }

    private fun createTimerScoreboard() {
        gameScoreboard = Bukkit.getScoreboardManager().newScoreboard

        objective = gameScoreboard?.registerNewObjective(
            "gametimer",
            org.bukkit.scoreboard.Criteria.DUMMY,
            Component.text("⏰ ТАЙМЕР ИГРЫ", NamedTextColor.GOLD)
        )

        objective?.displaySlot = DisplaySlot.SIDEBAR

        // Применяем scoreboard ко всем игрокам
        commands.keys.forEach { playerId ->
            getPlayer(playerId)!!.scoreboard = gameScoreboard!!
        }

        // Первоначальное обновление
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

        objective?.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")?.score = 8
        objective?.getScore("")?.score = 7
        objective?.getScore("§fОсталось времени:")?.score = 6
        objective?.getScore("$timeColorCode$timeString")?.score = 5
        objective?.getScore("  ")?.score = 4
        objective?.getScore("§7Режим: §aАКТИВЕН")?.score = 3
        objective?.getScore("§7Игроков: §b${Bukkit.getOnlinePlayers().size}")?.score = 2
        objective?.getScore("   ")?.score = 1
        objective?.getScore("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")?.score = 0
    }
}
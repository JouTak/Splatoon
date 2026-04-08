package ru.joutak.splatoon.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import ru.joutak.splatoon.scripts.Game
import ru.joutak.splatoon.scripts.GameManager
import ru.joutak.splatoon.scripts.giveBacillus
import ru.joutak.splatoon.scripts.giveSplatBomb
import java.time.Duration

class BoostPickupListener : Listener {

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player

        val game = GameManager.getGame(player)

        if (game == null) return

        val nearbyEntities = player.getNearbyEntities(0.5, 0.5, 0.5)

        if (nearbyEntities.isEmpty()) return

        val nearbyDisplays = nearbyEntities.filterIsInstance<ItemDisplay>()

        if (nearbyDisplays.isEmpty()) return

        nearbyDisplays.forEach { display ->
            when {
                display.scoreboardTags.contains("bomb") -> {
                    val t = Title.title(
                        Component.text("БУСТ ПОЛУЧЕН!", NamedTextColor.GOLD),
                        Component.text("Сплат-бомба", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(150))
                    )
                    player.showTitle(t)
                    player.inventory.addItem(giveSplatBomb())
                    delete(display, game)
                    return
                }
                display.scoreboardTags.contains("bacillus") -> {
                    val t = Title.title(
                        Component.text("БУСТ ПОЛУЧЕН!", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Бацилла", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(150))
                    )
                    player.showTitle(t)
                    player.inventory.addItem(giveBacillus())
                    delete(display, game)
                    return
                }
            }
        }
    }

    private fun delete(display: Display, game: Game) {
        var toRemove: List<Double> = emptyList()

        for (key in game.layingBoosts.keys) {
            if (display.uniqueId == game.layingBoosts[key]!!.uniqueId) {
                display.remove()
                toRemove = key
                break
            }
        }

        game.layingBoosts.remove(toRemove)
    }
}
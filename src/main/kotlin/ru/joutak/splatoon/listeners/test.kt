import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.joutak.splatoon.scripts.Stats

class JoinListener : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        Stats.playerGame[player.uniqueId]!!.commands += player.uniqueId to 0
        Stats.playerGame[player.uniqueId]!!.paintedPerson += player.uniqueId to 0
        Stats.playerGame[player.uniqueId]!!.startGame()
    }
}
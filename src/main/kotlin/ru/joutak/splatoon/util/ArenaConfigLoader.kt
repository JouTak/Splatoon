import org.bukkit.configuration.file.FileConfiguration
import ru.joutak.minigames.domain.GameInstanceConfig

object ArenaConfigLoader {
    fun loadArenas(config: FileConfiguration): List<GameInstanceConfig> {
        val arenasList = mutableListOf<GameInstanceConfig>()
        val arenas = config.getMapList("arenas")
        for (arena in arenas) {
            val id = arena["id"] as? String ?: continue
            val world = arena["world"] as? String ?: continue
            val teamCount = (arena["teamCount"] as? Int) ?: 2
            val playersPerTeam = (arena["playersPerTeam"] as? Int) ?: 3

            arenasList.add(
                GameInstanceConfig(
                    id = id,
                    teamCount = teamCount,
                    playersPerTeam = playersPerTeam,
                    meta = mapOf("world" to world)
                )
            )
        }
        return arenasList
    }
}

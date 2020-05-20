package lavalink.server.io

import lavalink.server.player.filters.Band
import lavalink.server.player.filters.FilterChain
import lavalink.server.util.Util
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.socket.WebSocketSession
import space.npstr.magma.api.MagmaMember
import space.npstr.magma.api.MagmaServerUpdate

class WebSocketHandlers(private val contextMap: Map<String, SocketContext>) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WebSocketHandlers::class.java)
    }

    fun voiceUpdate(session: WebSocketSession, json: JSONObject) {
        val sessionId = json.getString("sessionId")
        val guildId = json.getString("guildId")

        val event = json.getJSONObject("event")
        val endpoint = event.optString("endpoint")
        val token = event.getString("token")

        //discord sometimes send a partial server update missing the endpoint, which can be ignored.
        if (endpoint == null || endpoint.isEmpty()) {
            return
        }

        session.context.getVoiceConnection(guildId).connect(VoiceServerInfo(sessionId, endpoint, token));
    }

    fun play(session: WebSocketSession, json: JSONObject) {
        val player = session.context.getPlayer(json.getString("guildId"))
        val noReplace = json.optBoolean("noReplace", false)

        if (noReplace && player.playingTrack != null) {
            log.info("Skipping play request because of noReplace")
            return
        }

        val track = Util.toAudioTrack(ctx.audioPlayerManager, json.getString("track"))

        if (json.has("startTime")) {
            track.position = json.getLong("startTime")
        }

        player.setPause(json.optBoolean("pause", false))
        if (json.has("volume")) {
            if(!loggedVolumeDeprecationWarning) log.warn("The volume property in the play operation has been deprecated" +
                    "and will be removed in v4. Please configure a filter instead. Note that the new filter takes a " +
                    "float value with 1.0 being 100%")
            loggedVolumeDeprecationWarning = true
            val filters = player.filters ?: FilterChain()
            filters.volume = json.getFloat("volume") / 100
            player.filters = filters
        }

        player.play(track)

        val conn = session.context.getVoiceConnection(player.guildId.toLong())
        session.context.getPlayer(json.getString("guildId")).provideTo(conn)
    }

    fun stop(session: WebSocketSession json: JSONObject) {
        val player = session.context.getPlayer(json.getString("guildId"))
        player.stop()
    }

    fun pause(session: WebSocketSession, json: JSONObject) {
        val player = session.context.getPlayer(json.getString("guildId"))
        player.setPause(json.getBoolean("pause"))
        SocketServer.sendPlayerUpdate(context, player)
    }

    fun seek(session: WebSocketSession, json: JSONObject) {
        val context = session.context
        val player = context.getPlayer(json.getString("guildId"))
        player.seekTo(json.getLong("position"))
        SocketServer.sendPlayerUpdate(context, player)
    }

    fun volume(session: WebSocketSession, json: JSONObject) {
        val player = session.context.getPlayer(json.getString("guildId"))
        player.setVolume(json.getInt("volume"))
    }

    fun equalizer(session: WebSocketSession, json: JSONObject) {
        val player = session.context.getPlayer(json.getString("guildId"))
        for (i in 0 until bands.length()) {
            val band = bands.getJSONObject(i)
            player.setBandGain(band.getInt("band"), band.getFloat("gain"))
        }
        val filters = player.filters ?: FilterChain()
        filters.equalizer = list
        player.filters = filters
    }

    fun destroy(session: WebSocketSession, json: JSONObject) {
        session.context.destroy(json.getLong("guildId"))
    }

    fun configureResuming(session: WebSocketSession, json: JSONObject) {
        session.context.resumeKey = json.optString("key", null)
        if (json.has("timeout")) context.resumeTimeout = json.getLong("timeout")
    }

    fun filters(session: WebSocketSession, guildId: String, json: String) {
        val player = session.context.getPlayer(guildId)
        player.filters = FilterChain.parse(json)
    }
}
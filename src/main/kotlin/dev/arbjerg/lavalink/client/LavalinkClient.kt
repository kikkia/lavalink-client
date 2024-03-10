package dev.arbjerg.lavalink.client

import dev.arbjerg.lavalink.client.loadbalancing.ILoadBalancer
import dev.arbjerg.lavalink.client.loadbalancing.IRegionFilter
import dev.arbjerg.lavalink.client.loadbalancing.VoiceRegion
import dev.arbjerg.lavalink.client.loadbalancing.builtin.DefaultLoadBalancer
import dev.arbjerg.lavalink.internal.ReconnectTask
import dev.arbjerg.lavalink.protocol.v4.Message
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.Closeable
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @param userId ID of the bot for authenticating with Discord
 */
class LavalinkClient(val userId: Long) : Closeable, Disposable {
    private val internalNodes = CopyOnWriteArrayList<LavalinkNode>()
    private val linkMap = ConcurrentHashMap<Long, Link>()
    private var clientOpen = true

    // Immutable public list
    val nodes: List<LavalinkNode>
        get() = internalNodes.toList()

    val links: List<Link>
        get() = linkMap.values.toList()

    // Events forwarded from all nodes.
    private val sink: Sinks.Many<ClientEvent> = Sinks.many().multicast().onBackpressureBuffer()
    val flux: Flux<ClientEvent> = sink.asFlux()
    private val reference: Disposable = flux.subscribe()

    /**
     * To determine the best node, we use a load balancer.
     * It is recommended to not change the load balancer after you've connected to a voice channel.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var loadBalancer: ILoadBalancer = DefaultLoadBalancer(this)

    private val reconnectService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "lavalink-reconnect-thread").apply { isDaemon = true }
    }

    init {
        reconnectService.scheduleWithFixedDelay(ReconnectTask(this), 0, 500, TimeUnit.MILLISECONDS)
    }

    // TODO: configure resuming

    /**
     * Add a node to the client.
     *
     * @param name The name of your node
     * @param address The ip and port of your node
     * @param password The password of your node
     * @param regionFilter (not currently used) Allows you to limit your node to a specific discord voice region
     */
    @JvmOverloads
    @Deprecated("Use NodeOptions instead",
        ReplaceWith("addNode(NodeOptions.Builder()...build())")
    )
    fun addNode(name: String, address: URI, password: String, regionFilter: IRegionFilter? = null): LavalinkNode {
        return addNode(NodeOptions.Builder().setName(name).setServerUri(address).setPassword(password).setRegionFilter(regionFilter).build())
    }

    /**
     * Add a node to the client.
     *
     * @param nodeOptions a populated NodeOptionsObject
     */
    fun addNode(nodeOptions: NodeOptions): LavalinkNode {
        if (nodes.any { it.name == nodeOptions.name }) {
            throw IllegalStateException("Node with name '${nodeOptions.name}' already exists")
        }

        val node = LavalinkNode(nodeOptions, this)
        internalNodes.add(node)

        listenForNodeEvent(node)

        return node
    }

    /**
     * Remove a node by its [name].
     */
    fun removeNode(name: String): Boolean {
        val node = nodes.firstOrNull { it.name == name }

        if (node == null) {
            throw IllegalStateException("Node with name '$name' does not exist")
        }

        return removeNode(node)
    }

    /**
     * Disconnect and remove a node the client.
     */
    fun removeNode(node: LavalinkNode): Boolean {
        if (node !in internalNodes) {
            return false
        }

        node.close()

        internalNodes.remove(node)

        return true
    }

    /**
     * Get or crate a link between a guild and a node.
     *
     * @param guildId The id of the guild
     * @param region (not currently used) The target voice region of when to select a node
     */
    @Deprecated(
        message = "Method name unclear",
        replaceWith = ReplaceWith("getOrCreateLink(guildId, region)")
    )
    @JvmOverloads
    fun getLink(guildId: Long, region: VoiceRegion? = null) = getOrCreateLink(guildId, region)

    /**
     * Get or crate a link between a guild and a node.
     *
     * @param guildId The id of the guild
     * @param region (not currently used) The target voice region of when to select a node
     */
    @JvmOverloads
    fun getOrCreateLink(guildId: Long, region: VoiceRegion? = null): Link {
        if (!linkMap.containsKey(guildId)) {
            val bestNode = loadBalancer.selectNode(region)
            linkMap[guildId] = Link(guildId, bestNode)
        }

        return linkMap[guildId]!!
    }

    /**
     * Returns a [Link] if it exists in the cache.
     * If we select a link for voice updates, we don't know the region yet.
     */
    fun getLinkIfCached(guildId: Long): Link? = linkMap[guildId]

    /**
     * Finds all players on unavailable nodes and transfers them to [node].
     */
    internal fun transferOrphansTo(node: LavalinkNode) {
        // This *should* never happen, but just in case...
        if (!node.available) {
            return
        }

        val orphans = findOrphanedPlayers()

        orphans.mapNotNull { linkMap[it.guildId] }
            .forEach { link ->
                link.transferNode(node)
            }
    }

    /**
     * Finds all players that are on unavailable nodes.
     */
    private fun findOrphanedPlayers(): List<LavalinkPlayer> {
        val unavailableNodes = nodes.filter { !it.available }

        return unavailableNodes.flatMap { it.playerCache.values }
    }

    internal fun onNodeDisconnected(node: LavalinkNode) {
        // Don't do anything if we are shutting down.
        if (!clientOpen) {
            return
        }

        if (nodes.size == 1) {
            linkMap.forEach { (_, link) ->
                link.state = LinkState.DISCONNECTED
            }
            return
        }

        // If we have no nodes available, don't attempt to load-balance.
        if (nodes.all { !it.available }) {
            linkMap.filter { (_, link) -> link.node == node }
                .forEach { (_, link) ->
                    link.state = LinkState.DISCONNECTED
                }
            return
        }

        linkMap.forEach { (_, link) ->
            if (link.node == node) {
                val voiceRegion = link.cachedPlayer?.voiceRegion

                link.state = LinkState.CONNECTING
                // The delay is used to prevent a race condition in Discord, causing close code 4006
                link.transferNode(loadBalancer.selectNode(region = voiceRegion), delay = Duration.ofMillis(1000))
            }
        }
    }

    // For the java people
    /**
     * Listen to events from all nodes. Please note that uncaught exceptions will cause the listener to stop emitting events.
     *
     * @param type the [ClientEvent] to listen for
     *
     * @return a [Flux] of [ClientEvent]s
     */
    fun <T : ClientEvent> on(type: Class<T>): Flux<T> {
        return flux.ofType(type)
    }

    /**
     * Listen to events from all nodes. Please note that uncaught exceptions will cause the listener to stop emitting events.
     *
     * @return a [Flux] of [ClientEvent]s
     */
    inline fun <reified T : ClientEvent> on() = on(T::class.java)

    /**
     * Close the client and disconnect all nodes.
     */
    override fun close() {
        clientOpen = false
        reconnectService.shutdownNow()
        nodes.forEach { it.close() }
        reference.dispose()
    }

    override fun dispose() {
        close()
    }

    internal fun removeDestroyedLink(guildId: Long) {
        linkMap.remove(guildId)
    }

    private fun listenForNodeEvent(node: LavalinkNode) {
        node.on<ClientEvent>()
            .subscribe {
                try {
                    sink.tryEmitNext(it)
                } catch (e: Exception) {
                    sink.emitError(e, Sinks.EmitFailureHandler.FAIL_FAST)
                }
            }
    }
}

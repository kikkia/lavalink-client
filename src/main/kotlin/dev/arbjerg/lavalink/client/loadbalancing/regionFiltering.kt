package dev.arbjerg.lavalink.client.loadbalancing

import dev.arbjerg.lavalink.client.LavalinkNode

interface IRegionFilter {
    /** Whether this node may currently be used for this region */
    fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict
}

enum class RegionFilterVerdict {
    /** Allow this region to be used*/
    PASS,
    /** The load balancer should greatly prefer other nodes */
    SOFT_BLOCK,
    /** Do not use this node for this region under any circumstance */
    BLOCK
}

/**
 * Custom [IRegionFilter] that groups voice regions together.
 * You must register [dev.arbjerg.lavalink.client.loadbalancing.builtin.VoiceRegionPenaltyProvider] as a penalty provider in order for this filter to work.
 */
object RegionGroup {
    /**
     * An [IRegionFilter] for [VoiceRegion.SYDNEY], [VoiceRegion.INDIA], [VoiceRegion.JAPAN], [VoiceRegion.HONGKONG], [VoiceRegion.SINGAPORE], and [VoiceRegion.SOUTH_KOREA].
     */
    @JvmField
    val ASIA: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.SYDNEY, VoiceRegion.INDIA, VoiceRegion.JAPAN, VoiceRegion.HONGKONG, VoiceRegion.SINGAPORE, VoiceRegion.SOUTH_KOREA)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }

    /**
     * An [IRegionFilter] for [VoiceRegion.ROTTERDAM], [VoiceRegion.RUSSIA], [VoiceRegion.AMSTERDAM], [VoiceRegion.MADRID], [VoiceRegion.MILAN],
     * [VoiceRegion.BUCHAREST], [VoiceRegion.EUROPE], [VoiceRegion.LONDON], [VoiceRegion.FINLAND], [VoiceRegion.FRANKFURT], and [VoiceRegion.STOCKHOLM].
     */
    @JvmField
    val EUROPE: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.ROTTERDAM, VoiceRegion.RUSSIA, VoiceRegion.AMSTERDAM, VoiceRegion.MADRID, VoiceRegion.MILAN,
            VoiceRegion.BUCHAREST, VoiceRegion.EUROPE, VoiceRegion.LONDON, VoiceRegion.FINLAND, VoiceRegion.FRANKFURT, VoiceRegion.STOCKHOLM, VoiceRegion.WARSAW)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }

    /**
     * An [IRegionFilter] for [VoiceRegion.US_CENTRAL], [VoiceRegion.US_EAST], [VoiceRegion.US_WEST], [VoiceRegion.US_SOUTH], [VoiceRegion.ATLANTA],
     * [VoiceRegion.SEATTLE], [VoiceRegion.SANTA_CLARA], [VoiceRegion.NEWARK], [VoiceRegion.MONTREAL], [VoiceRegion.OREGON], and [VoiceRegion.ST_PETE].
     */
    @JvmField
    val US: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.US_CENTRAL, VoiceRegion.US_EAST, VoiceRegion.US_SOUTH, VoiceRegion.US_WEST, VoiceRegion.ATLANTA,
            VoiceRegion.SEATTLE, VoiceRegion.SANTA_CLARA, VoiceRegion.NEWARK, VoiceRegion.MONTREAL, VoiceRegion.OREGON, VoiceRegion.ST_PETE)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }

    /**
     * An [IRegionFilter] for [VoiceRegion.BRAZIL], [VoiceRegion.SANTIAGO], and [VoiceRegion.BUENOS_AIRES].
     */
    @JvmField
    val SOUTH_AMERICA: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.BRAZIL, VoiceRegion.SANTIAGO, VoiceRegion.BUENOS_AIRES)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }

    /**
     * An [IRegionFilter] for [VoiceRegion.SOUTH_AFRICA].
     */
    @JvmField
    val AFRICA: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.SOUTH_AFRICA)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }

    /**
     * An [IRegionFilter] for [VoiceRegion.TEL_AVIV] and [VoiceRegion.DUBAI].
     */
    @JvmField
    val MIDDLE_EAST: IRegionFilter = object : IRegionFilter {
        val regions = listOf(VoiceRegion.TEL_AVIV, VoiceRegion.DUBAI)

        override fun isRegionAllowed(node: LavalinkNode, region: VoiceRegion): RegionFilterVerdict {
            return if (region in regions) RegionFilterVerdict.PASS else RegionFilterVerdict.SOFT_BLOCK
        }
    }


    /**
     * Gets a [RegionGroup] from a string. This method is case-insensitive.
     *
     * @param region The region to get the [RegionGroup] for.
     * Valid values are [AFRICA], [ASIA], [EUROPE], [MIDDLE_EAST], [SOUTH_AMERICA] and [US].
     */
    fun valueOf(region: String) = when (region.uppercase()) {
        "AFRICA" -> AFRICA
        "ASIA" -> ASIA
        "EUROPE" -> EUROPE
        "MIDDLE_EAST" -> MIDDLE_EAST
        "SOUTH_AMERICA" -> SOUTH_AMERICA
        "US" -> US
        else -> throw IllegalArgumentException("No region constant: $region")
    }
}

// TODO In case no exact server match, should it look for the closest node in that same region?
enum class VoiceRegion(val id: String, val visibleName: String) {
    AMSTERDAM("amsterdam", "Amsterdam"),
    ATLANTA("atlanta", "Atlanta"),
    BRAZIL("brazil", "Brazil"),
    BUCHAREST("bucharest", "Bucharest"),
    BUENOS_AIRES("buenos-aires", "Brazil"),
    DUBAI("dubai", "Dubai"),
    EUROPE("europe", "Europe"),
    FINLAND("finland", "Finland"),
    FRANKFURT("frankfurt", "Frankfurt"),
    HONGKONG("hongkong", "Hong Kong"),
    INDIA("india", "India"),
    JAPAN("japan","Japan"),
    LONDON("london", "London"),
    MADRID("madrid", "Madrid"),
    MILAN("milan", "Milan"),
    MONTREAL("montreal", "Montreal"),
    NEWARK("newark", "Newark"),
    OREGON("oregon", "Oregon"),
    ROTTERDAM("rotterdam","Rotterdam"),
    RUSSIA("russia", "Russia"),
    SANTA_CLARA("santa-clara", "Santa Clara"),
    SANTIAGO("santiago", "Santiago"),
    SEATTLE("seattle", "Seattle"),
    SINGAPORE("singapore", "Singapore"),
    SOUTH_AFRICA("southafrica","South Africa"),
    SOUTH_KOREA("south-korea", "South Korea"),
    ST_PETE("st-pete", "St Pete"),
    STOCKHOLM("stockholm", "Stockholm"),
    SYDNEY("sydney", "Sydney"),
    TEL_AVIV("tel-aviv", "Tel Aviv"),
    US_CENTRAL("us-central", "US Central"),
    US_EAST("us-east", "US East"),
    US_SOUTH("us-south", "US South"),
    US_WEST("us-west", "US West"),
    WARSAW("warsaw", "Warsaw"),

    UNKNOWN("", "Unknown");

    companion object {
        @JvmStatic
        fun fromEndpoint(endpoint: String): VoiceRegion {
            // Endpoints come in format like "seattle1865.discord.gg", trim to subdomain with no numbers
            val endpointRegex = "^([a-z\\-]+)[0-9]+.*:443\$".toRegex()
            val match = endpointRegex.find(endpoint) ?: return UNKNOWN
            val idFromEndpoint = match.groupValues[1]
            return entries.find { it.id == idFromEndpoint } ?: UNKNOWN
        }
    }

    override fun toString(): String {
        return "${name}($id, $visibleName)"
    }
}

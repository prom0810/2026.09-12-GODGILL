package com.safewalk.map.model

import android.content.res.AssetManager
import java.io.IOException
import java.nio.charset.StandardCharsets

internal fun interface BundledRoadNetworkDataSource {
    @Throws(IOException::class)
    fun read(): String
}

internal class AssetRoadNetworkDataSource(
    private val assets: AssetManager,
    private val assetName: String = DEFAULT_ASSET_NAME,
) : BundledRoadNetworkDataSource {
    override fun read(): String = assets.open(assetName)
        .bufferedReader(StandardCharsets.UTF_8)
        .use { it.readText() }

    companion object {
        private const val DEFAULT_ASSET_NAME = "road_network.json"
    }
}

package com.safewalk.map.model

import java.io.IOException

/** 경로 탐색에 사용할 도로 그래프를 제공한다. */
fun interface RouteGraphProvider {
    @Throws(IOException::class)
    fun buildGraph(
        cctvSites: List<CctvSite>,
        securityLightSites: List<SecurityLightSite>,
        facilities: List<Facility>,
    ): RouteGraph
}

package com.safewalk.map.model

internal val VALID_OVERPASS_RESPONSE =
    """
        {
          "elements": [
            {"type": "node", "id": 1, "lat": 36.8100, "lon": 127.1460},
            {"type": "node", "id": 2, "lat": 36.8105, "lon": 127.1460},
            {"type": "way", "id": 10, "nodes": [1, 2]}
          ]
        }
    """.trimIndent()

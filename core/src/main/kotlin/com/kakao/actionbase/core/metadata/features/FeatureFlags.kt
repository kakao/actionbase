package com.kakao.actionbase.core.metadata.features

class FeatureFlags(
    baseline: List<Item>,
) {
    data class Item(
        val feature: Feature,
        val databases: Set<String> = emptySet(),
    )

    data class Scope(
        val database: String,
    )

    private val byDatabase: Map<String, Set<Feature>> = fold(baseline)

    fun has(
        scope: Scope,
        feature: Feature,
    ): Boolean = feature in byDatabase[scope.database].orEmpty()

    companion object {
        private fun fold(items: List<Item>): Map<String, Set<Feature>> {
            val byDatabase = mutableMapOf<String, MutableSet<Feature>>()
            items.forEach { (feature, databases) ->
                databases.forEach { database ->
                    byDatabase.getOrPut(database) { mutableSetOf() }.add(feature)
                }
            }
            return byDatabase.mapValues { it.value.toSet() }
        }
    }
}

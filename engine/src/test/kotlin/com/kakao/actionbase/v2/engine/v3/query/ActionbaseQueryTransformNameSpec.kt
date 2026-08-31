package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.engine.query.ActionbaseQuery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

/**
 * A transform's frame is added to the fetch context under the transform's name, and the response is
 * keyed the same way. Nothing downstream can tell a reused name from an intended one, so the query
 * refuses it rather than letting one frame quietly stand in for another.
 */
class ActionbaseQueryTransformNameSpec :
    StringSpec({

        fun fetch(name: String) =
            ActionbaseQuery.Item.Self(
                name = name,
                database = "commerce",
                table = "orders",
                source = ActionbaseQuery.Vertex.Value(listOf("user1")),
                include = true,
            )

        fun transform(name: String) = ActionbaseQuery.Transform.Sql(name = name, sql = "SELECT 1")

        "a transform cannot take the name of a fetch step" {
            shouldThrow<IllegalArgumentException> {
                ActionbaseQuery(fetch = listOf(fetch("hop1")), transform = listOf(transform("hop1")))
            }.message shouldContain "`hop1` already names a fetch step"
        }

        "two transforms cannot share a name" {
            shouldThrow<IllegalArgumentException> {
                ActionbaseQuery(fetch = listOf(fetch("hop1")), transform = listOf(transform("shaped"), transform("shaped")))
            }.message shouldContain "`shaped` already names a fetch step or an earlier transform"
        }

        "a transform keeps its own name" {
            ActionbaseQuery(fetch = listOf(fetch("hop1")), transform = listOf(transform("shaped"), transform("ranked")))
        }

        "a fetch step that is not returned still holds its name" {
            shouldThrow<IllegalArgumentException> {
                ActionbaseQuery(
                    fetch = listOf(fetch("hop1").copy(include = false)),
                    transform = listOf(transform("hop1")),
                )
            }
        }
    })

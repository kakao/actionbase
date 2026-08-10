package com.kakao.actionbase.server.control.metastore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetastorePropertiesTest {
    private fun metastore(
        url: String = "jdbc:mysql://ab-alpha-meta.example.net:3306/graph",
        table: String = MetastoreProperties.DEFAULT_TABLE,
    ) = MetastoreProperties.Metastore(url = url, user = "purge", password = "secret", table = table)

    private fun props(vararg entries: Pair<String, MetastoreProperties.Metastore>) = MetastoreProperties(entries.toMap())

    @Test
    fun `a configured metastore resolves by name`() {
        val registry = props("alpha" to metastore()).toRegistry()

        val target = registry.target("alpha")

        assertEquals("jdbc:mysql://ab-alpha-meta.example.net:3306/graph", target.url)
        assertEquals(MetastoreProperties.DEFAULT_TABLE, target.table)
    }

    @Test
    fun `a purge set is resolved back by url and table`() {
        val registry = props("alpha" to metastore()).toRegistry()

        assertEquals("alpha", registry.target("jdbc:mysql://ab-alpha-meta.example.net:3306/graph", "kc_graph_metadata").name)
    }

    @Test
    fun `a target absent from the configuration is refused rather than opened`() {
        val registry = props("alpha" to metastore()).toRegistry()

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                registry.target("jdbc:mysql://somewhere-else.example.net:3306/graph", "kc_graph_metadata")
            }
        assertTrue(thrown.message!!.contains("not configured"), thrown.message)
    }

    @Test
    fun `an unknown name names the ones that exist`() {
        val registry = props("alpha" to metastore()).toRegistry()

        val thrown = assertThrows(IllegalArgumentException::class.java) { registry.target("beta") }
        assertTrue(thrown.message!!.contains("alpha"), thrown.message)
    }

    @Test
    fun `a url that is not jdbc fails at startup rather than at the first request`() {
        assertThrows(IllegalArgumentException::class.java) {
            props("alpha" to metastore(url = "ab-alpha-meta.example.net:3306")).toRegistry()
        }
    }

    @Test
    fun `a table name that is not a plain identifier is rejected`() {
        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                props("alpha" to metastore(table = "kc_graph_metadata; drop table users")).toRegistry()
            }
        assertTrue(thrown.message!!.contains("identifier"), thrown.message)
    }

    @Test
    fun `two names pointing at the same table fail at startup, since a purge set could not resolve back to one`() {
        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                props("alpha" to metastore(), "alpha-copy" to metastore()).toRegistry()
            }
        assertTrue(thrown.message!!.contains("same url and table"), thrown.message)
    }

    @Test
    fun `the password is not printed, since a generated toString would carry it into any log line`() {
        val printed = metastore().toString()

        assertFalse(printed.contains("secret"), printed)
        assertTrue(printed.contains("jdbc:mysql://ab-alpha-meta.example.net:3306/graph"), printed)
        assertTrue(printed.contains("purge"), printed)
    }

    @Test
    fun `the enclosing properties object does not print it either, which is the object that gets logged`() {
        val printed = props("alpha" to metastore()).toString()

        assertFalse(printed.contains("secret"), printed)
    }

    @Test
    fun `no metastores configured is allowed, since a control instance need not host a purge`() {
        assertEquals(emptyList<Any>(), MetastoreProperties().toRegistry().targets)
    }
}

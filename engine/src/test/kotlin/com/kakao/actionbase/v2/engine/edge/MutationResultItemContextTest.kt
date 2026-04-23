package com.kakao.actionbase.v2.engine.edge

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class MutationResultItemContextTest {
    private val mapper = jacksonObjectMapper()

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: null context is omitted from JSON
          item:
            status: CREATED
            traceId: trace-1
            edge: null
            context: null
          expectedJson: '{"status":"CREATED","traceId":"trace-1","edge":null}'

        - name: non-null context is serialized
          item:
            status: CREATED
            traceId: trace-1
            edge: null
            context: { debug: "trace-1", size: 10 }
          expectedJson: '{"status":"CREATED","traceId":"trace-1","edge":null,"context":{"debug":"trace-1","size":10}}'
        """,
    )
    fun `Item serializes`(
        item: MutationResultItem,
        expectedJson: String,
    ) {
        assertEquals(expectedJson, mapper.writeValueAsString(item))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: missing context field deserializes to null
          json: '{"status":"CREATED","traceId":"trace-1","edge":null}'
          expectedContext: null

        - name: non-null context round-trips through deser
          json: '{"status":"UPDATED","traceId":"trace-2","edge":null,"context":{"k":"v","n":1}}'
          expectedContext: { k: v, n: 1 }
        """,
    )
    fun `Item deserializes`(
        json: String,
        expectedContext: Map<String, Any?>?,
    ) {
        val item = mapper.readValue<MutationResultItem>(json)
        assertEquals(expectedContext, item.context)
    }
}

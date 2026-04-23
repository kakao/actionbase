package com.kakao.actionbase.core.payload

import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class EdgeMutationResponseItemContextTest {
    private val mapper = jacksonObjectMapper()

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: null context is omitted from JSON
          item:
            source: 1
            target: 2
            status: CREATED
            count: 1
            context: null
          expectedJson: '{"source":1,"target":2,"status":"CREATED","count":1}'

        - name: non-null context is serialized
          item:
            source: 1
            target: 2
            status: CREATED
            count: 1
            context: { debug: "trace-1", size: 10 }
          expectedJson: '{"source":1,"target":2,"status":"CREATED","count":1,"context":{"debug":"trace-1","size":10}}'
        """,
    )
    fun `Item serializes`(
        item: EdgeMutationResponse.Item,
        expectedJson: String,
    ) {
        assertEquals(expectedJson, mapper.writeValueAsString(item))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: missing context field deserializes to null
          json: '{"source":1,"target":2,"status":"CREATED","count":1}'
          expectedContext: null

        - name: non-null context round-trips through deser
          json: '{"source":"s","target":"t","status":"CREATED","count":3,"context":{"k":"v"}}'
          expectedContext: { k: v }
        """,
    )
    fun `Item deserializes`(
        json: String,
        expectedContext: Map<String, Any?>?,
    ) {
        val item = mapper.readValue<EdgeMutationResponse.Item>(json)
        assertEquals(expectedContext, item.context)
    }
}

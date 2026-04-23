package com.kakao.actionbase.core.payload

import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationResponse
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class MultiEdgeMutationResponseItemContextTest {
    private val mapper = jacksonObjectMapper()

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: null context is omitted from JSON
          item:
            id: 100
            status: CREATED
            count: 1
            context: null
          expectedJson: '{"id":100,"status":"CREATED","count":1}'

        - name: non-null context is serialized
          item:
            id: 100
            status: CREATED
            count: 1
            context: { debug: "trace-1" }
          expectedJson: '{"id":100,"status":"CREATED","count":1,"context":{"debug":"trace-1"}}'
        """,
    )
    fun `Item serializes`(
        item: MultiEdgeMutationResponse.Item,
        expectedJson: String,
    ) {
        assertEquals(expectedJson, mapper.writeValueAsString(item))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: missing context field deserializes to null
          json: '{"id":100,"status":"CREATED","count":1}'
          expectedContext: null

        - name: non-null context round-trips through deser
          json: '{"id":42,"status":"UPDATED","count":2,"context":{"k":"v","n":1}}'
          expectedContext: { k: v, n: 1 }
        """,
    )
    fun `Item deserializes`(
        json: String,
        expectedContext: Map<String, Any?>?,
    ) {
        val item = mapper.readValue<MultiEdgeMutationResponse.Item>(json)
        assertEquals(expectedContext, item.context)
    }
}

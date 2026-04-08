package com.kakao.actionbase.engine.query.compat

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.sql.ScanFilter

fun ActionbaseQuery.Item.Scan.toScanFilter(srcSet: Set<Any>): ScanFilter =
    ScanFilter(
        name = EntityName(database, table),
        srcSet = srcSet,
        dir = direction,
        limit = limit ?: ScanFilter.defaultLimit,
        offset = offset,
        indexName = index,
        otherPredicates = predicates?.toSet() ?: emptySet(),
    )

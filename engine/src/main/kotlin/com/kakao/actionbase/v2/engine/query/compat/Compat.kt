package com.kakao.actionbase.v2.engine.query.compat

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.sql.ScanFilter

fun ActionbaseQuery.Item.Scan.toScanFilter(srcSet: Set<Any>): ScanFilter =
    ScanFilter(
        name = EntityName(service, label),
        srcSet = srcSet,
        dir = direction,
        limit = limit,
        offset = offset,
        indexName = index,
        otherPredicates = predicates?.toSet() ?: emptySet(),
    )

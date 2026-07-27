package org.tmt.peasproceduredataservice.core.models

import java.util.List

case class ComputationKeyValuePairList(
    procedureRunId: Int,
    keyValuePairList: List[ComputationKeyValuePair]
)

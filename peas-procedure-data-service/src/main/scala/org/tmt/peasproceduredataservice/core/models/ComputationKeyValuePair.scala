package org.tmt.peasproceduredataservice.core.models

import java.util.Optional

case class ComputationKeyValuePairKey(
    procedureRunId: Optional[Integer] = Optional.empty(),
    iterationNumber: Optional[Integer] = Optional.empty(),
    computationName: String,
    fieldName: String
)

case class ComputationKeyValuePair(
    key: ComputationKeyValuePairKey,
    value: GenericValue
)

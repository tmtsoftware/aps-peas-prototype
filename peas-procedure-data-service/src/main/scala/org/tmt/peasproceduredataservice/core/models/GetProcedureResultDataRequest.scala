package org.tmt.peasproceduredataservice.core.models

import java.util.List

case class GetProcedureResultDataRequest(
    procedureRunId: Int,
    computationResultKeys: List[ComputationResultKey]
)

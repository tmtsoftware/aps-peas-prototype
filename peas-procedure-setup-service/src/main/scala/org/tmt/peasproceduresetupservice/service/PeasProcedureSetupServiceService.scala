package org.tmt.peasproceduresetupservice.service

import org.tmt.peasproceduresetupservice.core.models.{SequenceTemplateList, SubstitutionParam}
import play.api.libs.json.JsValue

import scala.concurrent.Future

trait PeasProcedureSetupServiceService {
  def listTemplates(): Future[SequenceTemplateList]
  def loadTemplate(configPath: String): Future[JsValue]
  def buildSequence(template: JsValue, substitutions: List[SubstitutionParam]): Future[JsValue]
}

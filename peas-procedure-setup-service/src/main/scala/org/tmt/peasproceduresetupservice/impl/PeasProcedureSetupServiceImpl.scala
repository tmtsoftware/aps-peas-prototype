package org.tmt.peasproceduresetupservice.impl

import org.apache.pekko.actor.typed.ActorSystem
import csw.config.api.scaladsl.ConfigClientService
import org.tmt.peasproceduresetupservice.core.models.{SequenceTemplateList, SubstitutionParam}
import org.tmt.peasproceduresetupservice.service.PeasProcedureSetupServiceService
import play.api.libs.json.*

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class PeasProcedureSetupServiceImpl(configClient: ConfigClientService)(implicit
    ec: ExecutionContext,
    system: ActorSystem[?]
) extends PeasProcedureSetupServiceService {

  private val RefPrefix = "REF:"

  // Guards against REF cycles recursing forever.
  private val MaxRefDepth = 25

  def listTemplates(): Future[SequenceTemplateList] =
    Future.successful(SequenceTemplateList(List.empty))

  def loadTemplate(configPath: String): Future[JsValue] = {
    val path = Paths.get(configPath)
    configClient
      .getActive(path)
      .flatMap {
        case Some(configData) =>
          configData.toStringF(system).map(Json.parse)
        case None =>
          Future.failed(
            new RuntimeException(
              s"Config file not found or has no active version: $configPath"
            )
          )
      }
  }

  // REFs are resolved FIRST, fully flattening the tree (nested sequences become
  // stringified JSON embedded in StringKey params -- see resolveParam below), and only
  // THEN are substitutions applied. This lets a substitution target a step at any REF
  // depth, not just the top-level template -- applySubstitutions recurses into embedded
  // nested sequences itself (see recurseIntoNestedSequence) to reach them.
  def buildSequence(template: JsValue, substitutions: List[SubstitutionParam]): Future[JsValue] =
    resolveSequence(template, depth = 0).map(resolved => applySubstitutions(resolved, substitutions))

  // Apply substitutions to matching commands at any depth. A command's own paramSet is
  // checked directly; additionally, any StringKey param whose value is itself a resolved,
  // stringified nested sequence (post REF-resolution) is recursed into, so a substitution
  // can reach a step buried arbitrarily deep in a REF chain (e.g. rbsf-a1.json ->
  // rbsf-b1.json -> common-d1-rbsf.json -> takeGoodExposure).
  private def applySubstitutions(sequence: JsValue, substitutions: List[SubstitutionParam]): JsValue = {
    val commands = sequence.as[JsArray].value.toSeq.map { command =>
      val commandName = (command \ "commandName").asOpt[String].getOrElse("")
      val matching = substitutions.filter(_.stepName == commandName)
      val paramSet = (command \ "paramSet").asOpt[JsArray].getOrElse(JsArray.empty)
      val updatedParams = paramSet.value.toSeq.map { param =>
        val substituted = if (matching.isEmpty) param else applyParamSubstitution(param, matching)
        recurseIntoNestedSequence(substituted, substitutions)
      }
      command.as[JsObject] + ("paramSet" -> JsArray(updatedParams))
    }
    JsArray(commands)
  }

  // Each param is a wrapper object: { "IntKey": { keyName, values, units } }. Try each
  // known key type to find the keyName and match it against this command's substitutions.
  private def applyParamSubstitution(param: JsValue, matching: List[SubstitutionParam]): JsValue = {
    val keyTypes = Seq("IntKey", "FloatKey", "StringKey", "DoubleKey", "LongKey", "BooleanKey", "ChoiceKey")
    keyTypes.foldLeft(Option.empty[JsValue]) {
      case (Some(already), _) => Some(already)
      case (None, keyType) =>
        (param \ keyType \ "keyName").asOpt[String].flatMap { keyName =>
          matching.find(_.paramName == keyName).map { sub =>
            val inner = (param \ keyType).as[JsObject]
            val updatedInner = inner + ("values" -> JsArray(Seq(sub.paramValue)))
            Json.obj(keyType -> updatedInner)
          }
        }
    }.getOrElse(param)
  }

  // If this param is a StringKey whose value parses as a JSON array (i.e. a fully
  // REF-resolved nested sequence embedded as a string -- see resolveParam), recurse
  // applySubstitutions into it and re-embed the result as a stringified JSON array.
  // Not a REF: string at this point (those are already gone after resolveSequence) --
  // just a plain resolved sequence sitting in a StringKey value.
  private def recurseIntoNestedSequence(param: JsValue, substitutions: List[SubstitutionParam]): JsValue = {
    (param \ "StringKey").asOpt[JsObject] match {
      case Some(stringKey) =>
        val values = (stringKey \ "values").asOpt[JsArray].getOrElse(JsArray.empty)
        values.value.headOption.flatMap(_.asOpt[String]).flatMap(parseAsSequenceArray) match {
          case Some(nestedArray) =>
            val recursed = applySubstitutions(nestedArray, substitutions)
            val updatedValues = JsArray(Seq(JsString(Json.stringify(recursed))))
            val updatedKey = stringKey + ("values" -> updatedValues)
            Json.obj("StringKey" -> updatedKey)
          case None => param
        }
      case None => param
    }
  }

  private def parseAsSequenceArray(str: String): Option[JsArray] =
    Try(Json.parse(str)).toOption.collect { case arr: JsArray => arr }

  // Resolve every command in a sequence recursively, resolving REF: substitutions
  // including REFs nested inside other REFs.
  private def resolveSequence(sequence: JsValue, depth: Int): Future[JsValue] = {
    if (depth > MaxRefDepth)
      Future.failed(new RuntimeException(
        s"REF substitution exceeded max depth of $MaxRefDepth - possible REF cycle"
      ))
    else {
      val commands = sequence.as[JsArray].value.toSeq
      Future
        .traverse(commands)(resolveCommand(_, depth))
        .map(resolved => JsArray(resolved))
    }
  }

  private def resolveCommand(command: JsValue, depth: Int): Future[JsValue] = {
    val paramSet = (command \ "paramSet").asOpt[JsArray].getOrElse(JsArray.empty)
    Future
      .traverse(paramSet.value.toSeq)(resolveParam(_, depth))
      .map { resolvedParams =>
        command.as[JsObject] + ("paramSet" -> JsArray(resolvedParams))
      }
  }

  // If this param is a StringKey whose first value starts with "REF:", fetch
  // the referenced config path, recursively resolve any REF: entries within it,
  // then substitute the fully-resolved JSON serialised as a string.
  private def resolveParam(param: JsValue, depth: Int): Future[JsValue] = {
    (param \ "StringKey").asOpt[JsObject] match {
      case Some(stringKey) =>
        val values = (stringKey \ "values").asOpt[JsArray].getOrElse(JsArray.empty)
        values.value.headOption.flatMap(_.asOpt[String]).filter(_.startsWith(RefPrefix)) match {
          case Some(refValue) =>
            val configPath = refValue.stripPrefix(RefPrefix)
            for {
              refJson      <- loadTemplate(configPath)
              resolvedJson <- resolveSequence(refJson, depth + 1)
            } yield {
              val resolvedValues = JsArray(Seq(JsString(Json.stringify(resolvedJson))))
              val resolvedKey    = stringKey + ("values" -> resolvedValues)
              JsObject(Seq("StringKey" -> resolvedKey))
            }
          case None =>
            Future.successful(param)
        }
      case None =>
        Future.successful(param)
    }
  }
}
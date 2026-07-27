package org.tmt.peasproceduresetupservice

import caseapp.{CommandName, ExtraName, HelpMessage}

sealed trait PeasProcedureSetupServiceAppCommand

object PeasProcedureSetupServiceAppCommand {

  @CommandName("start")
  final case class StartOptions(
      @HelpMessage("port of the app")
      @ExtraName("p")
      port: Option[Int]
  ) extends PeasProcedureSetupServiceAppCommand

}

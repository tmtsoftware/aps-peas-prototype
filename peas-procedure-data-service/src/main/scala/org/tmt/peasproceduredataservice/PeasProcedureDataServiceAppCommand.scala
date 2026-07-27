package org.tmt.peasproceduredataservice

import caseapp.{CommandName, ExtraName, HelpMessage}

sealed trait PeasProcedureDataServiceAppCommand

object PeasProcedureDataServiceAppCommand {

  @CommandName("start")
  final case class StartOptions(
      @HelpMessage("port of the app")
      @ExtraName("p")
      port: Option[Int]
  ) extends PeasProcedureDataServiceAppCommand

}

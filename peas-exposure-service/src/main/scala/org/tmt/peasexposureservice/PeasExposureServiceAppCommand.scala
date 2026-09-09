package org.tmt.peasexposureservice

import caseapp.{CommandName, ExtraName, HelpMessage}

sealed trait PeasExposureServiceAppCommand

object PeasExposureServiceAppCommand {

  @CommandName("start")
  final case class StartOptions(
      @HelpMessage("port of the app")
      @ExtraName("p")
      port: Option[Int]
  ) extends PeasExposureServiceAppCommand

}

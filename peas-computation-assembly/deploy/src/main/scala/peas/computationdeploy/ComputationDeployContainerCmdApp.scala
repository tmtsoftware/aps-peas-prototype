package peas.computationdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object ComputationDeployContainerCmdApp {

  def main(args: Array[String]): Unit = {
    ContainerCmd.start("peas_computation_deploy_container_cmd_app", Subsystem.withNameInsensitive("APS"), args)
  }
}

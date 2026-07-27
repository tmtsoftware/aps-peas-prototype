package peas.computationdeploy

import csw.framework.deploy.hostconfig.HostConfig
import csw.prefix.models.Subsystem

object ComputationDeployHostConfigApp {

  def main(args: Array[String]): Unit = {
    HostConfig.start("peas_computation_deploy_host_config_app", Subsystem.withNameInsensitive("APS"), args)
  }
}

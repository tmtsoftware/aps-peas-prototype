package org.tmt.peasproceduresetupservice.impl

import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.peasproceduresetupservice.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

class PeasProcedureSetupServiceImplTest extends AnyWordSpec with Matchers {

  "PeasProcedureSetupServiceImpl" must {
    "greeting should return greeting response of 'Hello user'" in {
      val apsSubmitterPrototypeImpl = new PeasProcedureSetupServiceImpl()
      apsSubmitterPrototypeImpl.greeting(UserInfo("John", "Smith")).futureValue should ===(GreetResponse("Hello user: John Smith!!!"))
    }

    "adminGreeting should return greeting response of 'Hello admin user'" in {
      val apsSubmitterPrototypeImpl = new PeasProcedureSetupServiceImpl()
      apsSubmitterPrototypeImpl.adminGreeting(UserInfo("John", "Smith")).futureValue should ===(AdminGreetResponse("Hello admin user: John Smith!!!"))
    }
  }
}

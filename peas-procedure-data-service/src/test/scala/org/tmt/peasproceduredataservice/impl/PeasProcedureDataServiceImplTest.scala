package org.tmt.peasproceduredataservice.impl

import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.peasproceduredataservice.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

class PeasProcedureDataServiceImplTest extends AnyWordSpec with Matchers {

  "PeasProcedureDataServiceImpl" must {
    "greeting should return greeting response of 'Hello user'" in {
      val peasproceduredataserviceImpl = new PeasProcedureDataServiceImpl()
      peasproceduredataserviceImpl.greeting(UserInfo("John", "Smith")).futureValue should ===(GreetResponse("Hello user: John Smith!!!"))
    }

    "adminGreeting should return greeting response of 'Hello admin user'" in {
      val peasproceduredataserviceImpl = new PeasProcedureDataServiceImpl()
      peasproceduredataserviceImpl.adminGreeting(UserInfo("John", "Smith")).futureValue should ===(AdminGreetResponse("Hello admin user: John Smith!!!"))
    }
  }
}

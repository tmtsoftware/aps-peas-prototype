package org.tmt.peasproceduresetupservice.http

import java.util.concurrent.CompletableFuture

import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.peasproceduresetupservice.impl.JPeasProcedureSetupServiceImpl
import org.tmt.peasproceduresetupservice.core.models.GreetResponse

class JPeasProcedureSetupServiceImplWrapperTest extends AnyWordSpec with Matchers {

  "PeasProcedureSetupServiceImplWrapper" must {
    "delegate sayBye to JPeasProcedureSetupServiceImpl.sayBye" in {
      val jPeasProcedureSetupServiceImpl       = mock[JPeasProcedureSetupServiceImpl]
      val apsSubmitterPrototypeImplWrapper = new JPeasProcedureSetupServiceImplWrapper(jPeasProcedureSetupServiceImpl)

      val apsSubmitterPrototypeResponse = mock[GreetResponse]
      when(jPeasProcedureSetupServiceImpl.sayBye()).thenReturn(CompletableFuture.completedFuture(apsSubmitterPrototypeResponse))

      apsSubmitterPrototypeImplWrapper.sayBye().futureValue should ===(apsSubmitterPrototypeResponse)
      verify(jPeasProcedureSetupServiceImpl).sayBye()
    }
  }
}

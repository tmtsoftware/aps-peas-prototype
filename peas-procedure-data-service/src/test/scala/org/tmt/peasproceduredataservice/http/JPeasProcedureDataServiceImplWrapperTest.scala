package org.tmt.peasproceduredataservice.http

import java.util.concurrent.CompletableFuture

import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.peasproceduredataservice.impl.JPeasProcedureDataServiceImpl
import org.tmt.peasproceduredataservice.core.models.GreetResponse

class JPeasProcedureDataServiceImplWrapperTest extends AnyWordSpec with Matchers {

  "PeasProcedureDataServiceImplWrapper" must {
    "delegate sayBye to JPeasProcedureDataServiceImpl.sayBye" in {
      val jPeasProcedureDataServiceImpl       = mock[JPeasProcedureDataServiceImpl]
      val peasproceduredataserviceImplWrapper = new JPeasProcedureDataServiceImplWrapper(jPeasProcedureDataServiceImpl)

      val peasproceduredataserviceResponse = mock[GreetResponse]
      when(jPeasProcedureDataServiceImpl.sayBye()).thenReturn(CompletableFuture.completedFuture(peasproceduredataserviceResponse))

      peasproceduredataserviceImplWrapper.sayBye().futureValue should ===(peasproceduredataserviceResponse)
      verify(jPeasProcedureDataServiceImpl).sayBye()
    }
  }
}

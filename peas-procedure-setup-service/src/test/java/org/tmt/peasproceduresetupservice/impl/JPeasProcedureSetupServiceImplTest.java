package org.tmt.peasproceduresetupservice.impl;

import esw.http.template.wiring.JCswServices;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.scalatestplus.testng.TestNGSuite;
import org.tmt.peasproceduresetupservice.core.models.GreetResponse;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;

public class JPeasProcedureSetupServiceImplTest extends TestNGSuite {

  @Test
  public void shouldCallBye() throws ExecutionException, InterruptedException {
    JCswServices mock = Mockito.mock(JCswServices.class);
    JPeasProcedureSetupServiceImpl jPeasProcedureSetupService = new JPeasProcedureSetupServiceImpl(mock);
    GreetResponse greetResponse = new GreetResponse("Bye!!!");
    assertThat(jPeasProcedureSetupService.sayBye().get(), CoreMatchers.is(greetResponse));
  }
}

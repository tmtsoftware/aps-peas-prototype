package org.tmt.peasproceduredataservice.impl;

import esw.http.template.wiring.JCswServices;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.scalatestplus.testng.TestNGSuite;
import org.tmt.peasproceduredataservice.core.models.GreetResponse;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;

public class JPeasProcedureDataServiceImplTest extends TestNGSuite {

  @Test
  public void shouldCallBye() throws ExecutionException, InterruptedException {
    JCswServices mock = Mockito.mock(JCswServices.class);
    JPeasProcedureDataServiceImpl jPeasProcedureDataService = new JPeasProcedureDataServiceImpl(mock);
    GreetResponse greetResponse = new GreetResponse("Bye!!!");
    assertThat(jPeasProcedureDataService.sayBye().get(), CoreMatchers.is(greetResponse));
  }
}

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.module.cxf;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_IGNORE_METHOD_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_METHOD_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_USER_PROPERTY;
import static org.mule.service.http.api.HttpConstants.Method.POST;

import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.functional.functional.FunctionalTestNotification;
import org.mule.functional.functional.FunctionalTestNotificationListener;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.context.notification.ServerNotification;
import org.mule.runtime.core.util.IOUtils;
import org.mule.runtime.core.util.concurrent.Latch;
import org.mule.service.http.api.domain.ParameterMap;
import org.mule.service.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.service.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.service.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.api.domain.message.response.HttpResponse;
import org.mule.services.http.TestHttpClient;
import org.mule.tck.junit4.rule.DynamicPort;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

public class CxfCustomHttpHeaderTestCase extends AbstractCxfOverHttpExtensionTestCase
    implements FunctionalTestNotificationListener {

  private static final String REQUEST_PAYLOAD = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
      + "<soap:Body>\n" + "<ns1:onReceive xmlns:ns1=\"http://functional.functional.mule.org/\">\n"
      + "    <ns1:arg0 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"xsd:string\">Test String</ns1:arg0>\n"
      + "</ns1:onReceive>\n" + "</soap:Body>\n" + "</soap:Envelope>";

  private static final String SOAP_RESPONSE =
      "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns1:onReceiveResponse xmlns:ns1=\"http://functional.functional.mule.org/\"><ns1:return xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"xsd:string\">Test String Received</ns1:return></ns1:onReceiveResponse></soap:Body></soap:Envelope>";

  private List<Message> notificationMsgList = new ArrayList<>();
  private Latch latch = new Latch();
  @Rule
  public TestHttpClient httpClient = new TestHttpClient.Builder().build();

  @Rule
  public DynamicPort dynamicPort = new DynamicPort("port1");

  @Override
  protected String getConfigFile() {
    return "headers-conf-flow-httpn.xml";
  }

  @Override
  protected void doSetUp() throws Exception {
    muleContext.registerListener(this);
  }

  @Override
  protected void doTearDown() throws Exception {
    muleContext.unregisterListener(this);
  }

  @Test
  public void testCxf() throws Exception {
    String endpointAddress = "http://localhost:" + dynamicPort.getValue() + "/services/TestComponent";

    String myProperty = "myProperty";

    ParameterMap headersMap = new ParameterMap();
    headersMap.put(MULE_USER_PROPERTY, "alan");
    headersMap.put(MULE_METHOD_PROPERTY, "onReceive");
    headersMap.put(myProperty, myProperty);

    HttpRequest request = HttpRequest.builder().setUri(endpointAddress).setMethod(POST).setHeaders(headersMap)
        .setEntity(new ByteArrayHttpEntity(REQUEST_PAYLOAD.getBytes())).build();

    HttpResponse response = httpClient.send(request, RECEIVE_TIMEOUT, false, null);
    String payload = IOUtils.toString(((InputStreamHttpEntity) response.getEntity()).getInputStream());
    assertEquals(SOAP_RESPONSE, payload);

    latch.await(3000, SECONDS);

    assertEquals(1, notificationMsgList.size());

    final HttpRequestAttributes attributes = (HttpRequestAttributes) notificationMsgList.get(0).getAttributes();
    // MULE_USER should be allowed in
    // TODO MULE-9857 Make message properties case sensitive
    assertThat(attributes.getHeaders().get(MULE_USER_PROPERTY.toLowerCase()), is("alan"));

    // mule properties should be removed
    // TODO MULE-9857 Make message properties case sensitive
    assertThat(attributes.getHeaders().get(MULE_IGNORE_METHOD_PROPERTY.toLowerCase()), nullValue());

    // custom properties should be allowed in
    // TODO MULE-9857 Make message properties case sensitive
    assertThat(attributes.getHeaders().get(myProperty.toLowerCase()), is(myProperty));
  }

  @Override
  public void onNotification(ServerNotification notification) {
    if (notification instanceof FunctionalTestNotification) {
      notificationMsgList.add(((FunctionalTestNotification) notification).getEventContext().getMessage());
      latch.release();
    } else {
      fail("invalid notification: " + notification);
    }
  }
}

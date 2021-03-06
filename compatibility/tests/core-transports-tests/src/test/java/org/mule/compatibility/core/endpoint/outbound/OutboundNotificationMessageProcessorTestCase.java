/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.core.endpoint.outbound;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.compatibility.core.context.notification.EndpointMessageNotification.MESSAGE_DISPATCH_END;
import static org.mule.compatibility.core.context.notification.EndpointMessageNotification.MESSAGE_SEND_END;
import static org.mule.runtime.core.api.MessageExchangePattern.ONE_WAY;
import static org.mule.runtime.core.api.MessageExchangePattern.REQUEST_RESPONSE;

import org.mule.compatibility.core.api.endpoint.OutboundEndpoint;
import org.mule.compatibility.core.processor.AbstractMessageProcessorTestCase;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.processor.Processor;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class OutboundNotificationMessageProcessorTestCase extends AbstractMessageProcessorTestCase {

  @Test
  public void testDispatch() throws Exception {
    TestEndpointMessageNotificationListener listener = new TestEndpointMessageNotificationListener();
    muleContext.registerListener(listener);

    OutboundEndpoint endpoint = createTestOutboundEndpoint(null, null, null, null, ONE_WAY, null);
    Processor mp = new OutboundNotificationMessageProcessor(endpoint);
    Event event = createTestOutboundEvent();
    mp.process(event);

    assertMessageNotification(listener, endpoint, event, MESSAGE_DISPATCH_END);
  }

  @Test
  public void testSend() throws Exception {
    TestEndpointMessageNotificationListener listener = new TestEndpointMessageNotificationListener();
    muleContext.registerListener(listener);

    OutboundEndpoint endpoint = createTestOutboundEndpoint(null, null, null, null, REQUEST_RESPONSE, null);
    Processor mp = new OutboundNotificationMessageProcessor(endpoint);
    Event event = createTestOutboundEvent();
    mp.process(event);

    assertMessageNotification(listener, endpoint, event, MESSAGE_SEND_END);
  }

  private void assertMessageNotification(TestEndpointMessageNotificationListener listener, OutboundEndpoint endpoint,
                                         Event event, int action)
      throws InterruptedException {
    assertThat(listener.latch.await(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS), is(true));
    assertThat(listener.messageNotification.getAction(), equalTo(action));
    assertThat(listener.messageNotification.getEndpoint(), equalTo(endpoint.getEndpointURI().getUri().toString()));
    assertThat(listener.messageNotification.getSource(), instanceOf(InternalMessage.class));
    assertThat(listener.messageNotification.getSource().getPayload().getValue(),
               equalTo(event.getMessage().getPayload().getValue()));
  }

}

package org.mule.test.integration.routing.replyto;

import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.Test;
import org.mule.tck.functional.EventCallback;
import org.mule.tck.functional.FunctionalTestComponent;
import org.mule.tck.junit4.FunctionalTestCase;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThat;

public class ReplyToChainIntegration5TestCase extends FunctionalTestCase
{

    public static final String TEST_PAYLOAD = "test payload";
    public static final String EXPECTED_PAYLOAD = TEST_PAYLOAD + " modified";
    public static final int TIMEOUT = 5000;

    @Override
    protected String getConfigResources()
    {
        return "org/mule/test/integration/routing/replyto/replyto-chain-integration-test-5.xml";
    }

    @Test
    public void testReplyToIsHonoredInFlowUsingAsyncBlock() throws Exception
    {
        org.mule.api.client.LocalMuleClient client = muleContext.getClient();
        final org.mule.util.concurrent.Latch flowExecutedLatch = new org.mule.util.concurrent.Latch();
        FunctionalTestComponent ftc = getFunctionalTestComponent("replierService");
        ftc.setEventCallback(new EventCallback()
        {
            @Override
            public void eventReceived(org.mule.api.MuleEventContext context, Object component) throws Exception
            {
                flowExecutedLatch.release();
            }
        });
        org.mule.api.MuleMessage muleMessage = new org.mule.DefaultMuleMessage(TEST_PAYLOAD, muleContext);
        muleMessage.setOutboundProperty(org.mule.api.config.MuleProperties.MULE_REPLY_TO_PROPERTY,"jms://response");
        client.dispatch("jms://jmsIn1", muleMessage);
        flowExecutedLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        org.mule.api.MuleMessage response = client.request("jms://response", TIMEOUT);
        assertThat(response, IsNull.<Object>notNullValue());
        assertThat(response.getPayloadAsString(), Is.is(EXPECTED_PAYLOAD));
    }
}

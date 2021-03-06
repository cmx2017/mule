/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.transport.http.functional;

import static java.lang.String.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.mule.functional.extensions.CompatibilityFunctionalTestCase;
import org.mule.service.http.api.HttpConstants;
import org.mule.tck.junit4.rule.DynamicPort;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;

import org.junit.Rule;
import org.junit.Test;

public class HttpUriEncodingErrorTestCase extends CompatibilityFunctionalTestCase {

  @Rule
  public DynamicPort dynamicPort = new DynamicPort("port");

  @Test
  public void failsWithAppropriateError() throws Exception {
    String address = getUri() + "?blah=badcode%2";
    Response response = sendGetRequest(address);

    assertThat(response.getStatusCode(), is(HttpConstants.HttpStatus.BAD_REQUEST.getStatusCode()));
    assertThat(response.getResponseBody(), containsString("Incorrectly encoded URL"));
  }

  @Test
  public void worksWhenValidUri() throws Exception {
    String address = getUri() + "?blah=a%20space";
    Response response = sendGetRequest(address);

    assertThat(response.getStatusCode(), is(HttpConstants.HttpStatus.OK.getStatusCode()));
    assertThat(response.getResponseBody(), equalTo("response"));
  }

  @Override
  protected String getConfigFile() {
    return "http-uri-encoding-error-config.xml";
  }

  private String getUri() {
    return format("http://localhost:%d/", dynamicPort.getNumber());
  }

  private Response sendGetRequest(String address) throws Exception {
    AsyncHttpClientConfig.Builder configBuilder = new AsyncHttpClientConfig.Builder();
    AsyncHttpClientConfig config = configBuilder.build();

    try (AsyncHttpClient asyncHttpClient = new AsyncHttpClient(new GrizzlyAsyncHttpProvider(config), config)) {
      ListenableFuture<Response> future = asyncHttpClient.prepareGet(address).execute();
      return future.get();
    }
  }
}

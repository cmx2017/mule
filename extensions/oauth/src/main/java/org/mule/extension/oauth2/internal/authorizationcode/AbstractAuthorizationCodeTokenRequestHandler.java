/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.oauth2.internal.authorizationcode;

import static org.mule.extension.oauth2.internal.authorizationcode.RequestHandlerUtils.addRequestHandler;
import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import static org.mule.service.http.api.HttpConstants.Method.GET;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.extension.oauth2.internal.AbstractTokenRequestHandler;
import org.mule.extension.oauth2.internal.authorizationcode.state.ResourceOwnerOAuthContext;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.service.http.api.server.RequestHandlerManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;

import org.slf4j.Logger;

/**
 * Base class for token request handler.
 */
public abstract class AbstractAuthorizationCodeTokenRequestHandler extends AbstractTokenRequestHandler {

  private static final Logger LOGGER = getLogger(AbstractAuthorizationCodeTokenRequestHandler.class);

  private AuthorizationCodeGrantType oauthConfig;
  private RequestHandlerManager redirectUrlHandlerManager;


  /**
   * Updates the access token by calling the token url with refresh token grant type
   *
   * @param resourceOwnerId the resource owner id to update
   */
  public void refreshToken(String resourceOwnerId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Executing refresh token for user " + resourceOwnerId);
    }
    final ResourceOwnerOAuthContext resourceOwnerOAuthContext =
        getOauthConfig().getUserOAuthContext().getContextForResourceOwner(resourceOwnerId);
    final boolean lockWasAcquired = resourceOwnerOAuthContext.getRefreshUserOAuthContextLock().tryLock();
    try {
      if (lockWasAcquired) {
        doRefreshToken(resourceOwnerOAuthContext);
        getOauthConfig().getUserOAuthContext().updateResourceOwnerOAuthContext(resourceOwnerOAuthContext);
      }
    } finally {
      if (lockWasAcquired) {
        resourceOwnerOAuthContext.getRefreshUserOAuthContextLock().unlock();
      }
    }
    if (!lockWasAcquired) {
      // if we couldn't acquire the lock then we wait until the other thread updates the token.
      waitUntilLockGetsReleased(resourceOwnerOAuthContext);
    }
  }

  /**
   * ThreadSafe refresh token operation to be implemented by subclasses
   *
   * @param resourceOwnerOAuthContext user oauth context object.
   */
  protected abstract void doRefreshToken(final ResourceOwnerOAuthContext resourceOwnerOAuthContext);

  private void waitUntilLockGetsReleased(ResourceOwnerOAuthContext resourceOwnerOAuthContext) {
    resourceOwnerOAuthContext.getRefreshUserOAuthContextLock().lock();
    resourceOwnerOAuthContext.getRefreshUserOAuthContextLock().unlock();
  }

  /**
   * @param oauthConfig oauth config for this token request handler.
   */
  public void setOauthConfig(AuthorizationCodeGrantType oauthConfig) {
    this.setTlsContextFactory(oauthConfig.getTlsContext());
    this.oauthConfig = oauthConfig;
  }

  public AuthorizationCodeGrantType getOauthConfig() {
    return oauthConfig;
  }

  /**
   * initialization method after configuration.
   */
  public void init() throws MuleException {}

  protected void createListenerForCallbackUrl() throws MuleException {
    final String callbackPath;

    if (getOauthConfig().getLocalCallbackUrl() != null) {
      try {
        final URL localCallbackUrl = new URL(getOauthConfig().getLocalCallbackUrl());
        callbackPath = localCallbackUrl.getPath();
      } catch (MalformedURLException e) {
        LOGGER.warn("Could not parse provided url %s. Validate that the url is correct", getOauthConfig().getLocalCallbackUrl());
        throw new DefaultMuleException(e);
      }
    } else if (getOauthConfig().getLocalCallbackConfig() != null) {
      // TODO MULE-11276 - Need a way to reuse an http listener declared in the application/domain")
      callbackPath = getOauthConfig().getLocalCallbackConfigPath();
    } else {
      throw new IllegalStateException("No localCallbackUrl or localCallbackConfig defined.");
    }

    this.redirectUrlHandlerManager =
        addRequestHandler(getOauthConfig().getServer(), GET, callbackPath, getDefaultEncoding(muleContext),
                          createRedirectUrlProcessor(), LOGGER);
  }

  @Override
  public void start() {
    redirectUrlHandlerManager.start();
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    redirectUrlHandlerManager.stop();
  }

  protected abstract Function<Result<Object, HttpRequestAttributes>, Result<String, HttpResponseAttributes>> createRedirectUrlProcessor();
}

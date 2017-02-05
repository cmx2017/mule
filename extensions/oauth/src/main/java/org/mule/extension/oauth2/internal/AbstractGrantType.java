/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.oauth2.internal;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static org.mule.extension.http.internal.HttpConnectorConstants.TLS_CONFIGURATION;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.SECURITY_TAB;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.request.authentication.HttpAuthentication;
import org.mule.extension.oauth2.internal.tokenmanager.TokenManagerConfig;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.registry.RegistrationException;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.runtime.operation.ParameterResolver;
import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.runtime.oauth.api.OAuthService;
import org.mule.runtime.oauth.api.builder.OAuthDancerBuilder;

import java.util.List;

import org.slf4j.Logger;

/**
 * Common interface for all grant types must extend this interface.
 * 
 * @since 4.0
 */
// TODO MULE-11412 Remove MuleContextAware
public abstract class AbstractGrantType implements HttpAuthentication, MuleContextAware, Lifecycle {

  private static final Logger LOGGER = getLogger(AbstractGrantType.class);

  // Expressions to extract parameters from standard token url response.
  private static final String ACCESS_TOKEN_EXPRESSION = "#[(payload match /.*\"access_token\"[ ]*:[ ]*\"([^\\\"]*)\".*/)[1]]";
  private static final String REFRESH_TOKEN_EXPRESSION = "#[(payload match /.*\"refresh_token\"[ ]*:[ ]*\"([^\\\"]*)\".*/)[1]]";
  private static final String EXPIRATION_TIME_EXPRESSION = "#[(payload match /.*\"expires_in\"[ ]*:[ ]*\"([^\\\"]*)\".*/)[1]]";

  // TODO MULE-11412 Add @Inject
  protected MuleContext muleContext;

  protected DeferredExpressionResolver resolver;

  /**
   * Application identifier as defined in the oauth authentication server.
   */
  @Parameter
  private String clientId;

  /**
   * Application secret as defined in the oauth authentication server.
   */
  @Parameter
  private String clientSecret;

  /**
   * Scope required by this application to execute. Scopes define permissions over resources.
   */
  @Parameter
  @Optional
  private String scopes;

  /**
   * The token manager configuration to use for this grant type.
   */
  @Parameter
  @Optional
  protected TokenManagerConfig tokenManager;

  /**
   * The oauth authentication server url to get access to the token. Mule, after receiving the authentication code from the oauth
   * server (through the redirectUrl) will call this url to get the access token.
   */
  @Parameter
  private String tokenUrl;

  /**
   * MEL expression to extract the access token parameter from the response of the call to tokenUrl.
   */
  @Parameter
  @Optional(defaultValue = ACCESS_TOKEN_EXPRESSION)
  protected ParameterResolver<String> responseAccessToken;

  @Parameter
  @Optional(defaultValue = REFRESH_TOKEN_EXPRESSION)
  protected ParameterResolver<String> responseRefreshToken;

  /**
   * MEL expression to extract the expiresIn parameter from the response of the call to tokenUrl.
   */
  @Parameter
  @Optional(defaultValue = EXPIRATION_TIME_EXPRESSION)
  protected ParameterResolver<String> responseExpiresIn;

  @Parameter
  @Alias("custom-parameter-extractors")
  @Optional
  protected List<ParameterExtractor> parameterExtractors;

  /**
   * After executing an API call authenticated with OAuth it may be that the access token used was expired, so this attribute
   * allows for an expressions that will be evaluated against the http response of the API callback to determine if the request
   * failed because it was done using an expired token. In case the evaluation returns true (access token expired) then mule will
   * automatically trigger a refresh token flow and retry the API callback using a new access token. Default value evaluates if
   * the response status code was 401 or 403.
   */
  @Parameter
  @Optional(defaultValue = "#[attributes.statusCode == 401 or attributes.statusCode == 403]")
  private ParameterResolver<Boolean> refreshTokenWhen;

  /**
   * References a TLS config that will be used to receive incoming HTTP request and do HTTP request during the OAuth dance.
   */
  @Parameter
  @Optional
  @Expression(NOT_SUPPORTED)
  @DisplayName(TLS_CONFIGURATION)
  @Placement(tab = SECURITY_TAB)
  private TlsContextFactory tlsContextFactory;

  protected OAuthDancer dancer;

  @Override
  public final void initialise() throws InitialisationException {
    if (tokenManager == null) {
      this.tokenManager = TokenManagerConfig.createDefault(muleContext);
    }
    initialiseIfNeeded(tokenManager, muleContext);

    if (getTlsContextFactory() != null) {
      initialiseIfNeeded(getTlsContextFactory());
    }

    try {
      dancer = configDancer(muleContext.getRegistry().lookupObject(OAuthService.class))
          .clientCredentials(getClientId(), getClientSecret())
          .tokenUrl(getTokenUrl())
          .scopes(getScopes())
          .encoding(getDefaultEncoding(muleContext))
          .tlsContextFactory(getTlsContextFactory())
          .responseAccessTokenExpr(resolver.getExpression(getResponseAccessToken()))
          .responseRefreshTokenExpr(resolver.getExpression(getResponseRefreshToken()))
          .responseExpiresInExpr(resolver.getExpression(getResponseExpiresIn()))
          .customParametersExtractorsExprs(getCustomParameterExtractors().stream()
              .collect(toMap(extractor -> extractor.getParamName(),
                             extractor -> resolver.getExpression(extractor.getValue()))))
          .build();
    } catch (RegistrationException e) {
      throw new InitialisationException(e, this);
    }
    initialiseIfNeeded(dancer);
  }

  protected abstract OAuthDancerBuilder configDancer(OAuthService oauthService) throws InitialisationException;

  @Override
  public void start() throws MuleException {
    startIfNeeded(dancer);
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(dancer);
  }

  @Override
  public void dispose() {
    disposeIfNeeded(dancer, LOGGER);
  }

  /**
   * @param accessToken an oauth access token
   * @return the content of the HTTP authentication header.
   */
  protected String buildAuthorizationHeaderContent(String accessToken) {
    return "Bearer " + accessToken;
  }

  @Override
  public void setMuleContext(MuleContext muleContext) {
    this.muleContext = muleContext;
    this.resolver = new DeferredExpressionResolver(muleContext);
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getClientId() {
    return clientId;
  }

  public String getScopes() {
    return scopes;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public ParameterResolver<Boolean> getRefreshTokenWhen() {
    return refreshTokenWhen;
  }

  public ParameterResolver<String> getResponseAccessToken() {
    return responseAccessToken;
  }


  public ParameterResolver<String> getResponseRefreshToken() {
    return responseRefreshToken;
  }


  public ParameterResolver<String> getResponseExpiresIn() {
    return responseExpiresIn;
  }

  public List<ParameterExtractor> getCustomParameterExtractors() {
    return parameterExtractors != null ? parameterExtractors : emptyList();
  }

  public TlsContextFactory getTlsContextFactory() {
    return tlsContextFactory;
  }
}

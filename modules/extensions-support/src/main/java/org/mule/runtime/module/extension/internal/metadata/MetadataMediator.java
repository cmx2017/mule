/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.metadata;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.metadata.resolving.MetadataFailure.Builder.newFailure;
import static org.mule.runtime.api.metadata.resolving.MetadataResult.failure;
import static org.mule.runtime.api.metadata.resolving.MetadataResult.success;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.metadata.MetadataAttributes;
import org.mule.runtime.api.metadata.MetadataContext;
import org.mule.runtime.api.metadata.MetadataKey;
import org.mule.runtime.api.metadata.MetadataKeyProvider;
import org.mule.runtime.api.metadata.MetadataKeysContainer;
import org.mule.runtime.api.metadata.MetadataResolvingException;
import org.mule.runtime.api.metadata.descriptor.ComponentMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.InputMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.OutputMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.TypeMetadataDescriptor;
import org.mule.runtime.api.metadata.resolving.InputTypeResolver;
import org.mule.runtime.api.metadata.resolving.MetadataFailure;
import org.mule.runtime.api.metadata.resolving.MetadataResult;
import org.mule.runtime.api.metadata.resolving.OutputTypeResolver;
import org.mule.runtime.api.metadata.resolving.TypeKeysResolver;
import org.mule.runtime.extension.api.annotation.metadata.MetadataKeyId;
import org.mule.runtime.extension.api.metadata.MetadataResolverFactory;
import org.mule.runtime.extension.api.metadata.NullMetadataKey;
import org.mule.runtime.extension.internal.property.MetadataKeyIdModelProperty;
import org.mule.runtime.extension.internal.property.MetadataKeyPartModelProperty;
import org.mule.runtime.module.extension.internal.runtime.ParameterValueResolver;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a Component's Metadata by coordinating the several moving parts that are affected by the Metadata fetching process, so
 * that such pieces can remain decoupled.
 * <p/>
 * This mediator will coordinate the resolvers: {@link MetadataResolverFactory}, {@link TypeKeysResolver},
 * {@link InputTypeResolver} and {@link OutputTypeResolver}, and the descriptors that represent their results:
 * {@link ComponentMetadataDescriptor}, {@link OutputMetadataDescriptor} and {@link TypeMetadataDescriptor}
 *
 * @since 4.0
 */
public final class MetadataMediator<T extends ComponentModel<T>> {

  protected final T component;
  private final List<ParameterModel> metadataKeyParts;
  private final MetadataKeysDelegate keysDelegate;
  private final MetadataOutputDelegate outputDelegate;
  private final MetadataInputDelegate inputDelegate;
  private final MetadataKeyIdObjectResolver keyIdObjectResolver;
  private String keyContainerName = null;

  public MetadataMediator(T componentModel) {
    this.component = componentModel;
    this.metadataKeyParts = getMetadataKeyParts(componentModel);
    this.keysDelegate = new MetadataKeysDelegate(componentModel, metadataKeyParts);
    this.keyIdObjectResolver = new MetadataKeyIdObjectResolver(component);
    this.outputDelegate = new MetadataOutputDelegate(componentModel);
    this.inputDelegate = new MetadataInputDelegate(componentModel);

    componentModel.getModelProperty(MetadataKeyIdModelProperty.class)
        .ifPresent(keyIdMP -> keyContainerName = keyIdMP.getParameterName());
  }

  /**
   * Resolves the list of types available for the Content or Output of the associated {@link MetadataKeyProvider} Component,
   * representing them as a list of {@link MetadataKey}.
   * <p>
   * If no {@link MetadataKeyId} is present in the component's input parameters, then a {@link NullMetadataKey} is returned.
   * Otherwise, the {@link TypeKeysResolver#getKeys} associated with the current Component will be invoked to obtain
   * the keys
   *
   * @param context current {@link MetadataContext} that will be used by the {@link TypeKeysResolver}
   * @return Successful {@link MetadataResult} if the keys are obtained without errors Failure {@link MetadataResult} when no
   * Dynamic keys are a available or the retrieval fails for any reason
   */
  public MetadataResult<MetadataKeysContainer> getMetadataKeys(MetadataContext context) {
    return keysDelegate.getMetadataKeys(context);
  }

  /**
   * Resolves the {@link ComponentMetadataDescriptor} for the associated {@code context} using the specified
   * {@code key}
   *
   * @param context current {@link MetadataContext} that will be used by the metadata resolvers.
   * @param key     {@link MetadataKey} of the type which's structure has to be resolved, used both for input and output types
   * @return Successful {@link MetadataResult} if the MetadataTypes are resolved without errors Failure {@link MetadataResult}
   * when the Metadata retrieval of any element fails for any reason
   */
  public MetadataResult<ComponentMetadataDescriptor<T>> getMetadata(MetadataContext context, MetadataKey key) {
    try {
      Object resolvedKey = keyIdObjectResolver.resolve(key);
      return getMetadata(context, p -> resolvedKey, MetadataAttributes.builder().withKey(key));
    } catch (MetadataResolvingException e) {
      return failure(newFailure(e).onComponent());
    }
  }

  public MetadataResult<ComponentMetadataDescriptor<T>> getMetadata(MetadataContext context,
                                                                    ParameterValueResolver metadataKeyResolver) {
    return getMetadata(context, metadataKeyResolver, MetadataAttributes.builder());
  }

  /**
   * Resolves the {@link ComponentMetadataDescriptor} for the associated {@code context} using static and
   * dynamic resolving of the Component parameters, attributes and output.
   *
   * @param context current {@link MetadataContext} that will be used by the {@link InputTypeResolver} and
   *                {@link OutputTypeResolver}
   * @param metadataKeyResolver     {@link MetadataKey} of the type which's structure has to be resolved, used both for input and output types
   * @return Successful {@link MetadataResult} if the MetadataTypes are resolved without errors Failure {@link MetadataResult}
   * when the Metadata retrieval of any element fails for any reason
   */
  private MetadataResult<ComponentMetadataDescriptor<T>> getMetadata(MetadataContext context,
                                                                     ParameterValueResolver metadataKeyResolver,
                                                                     MetadataAttributes.MetadataAttributesBuilder attributesBuilder) {
    Object keyValue;
    MetadataResult keyValueResult = getMetadataKeyObjectValue(metadataKeyResolver);
    if (!keyValueResult.isSuccess()) {
      return keyValueResult;
    } else {
      keyValue = keyValueResult.get();
    }

    MetadataResult<OutputMetadataDescriptor> output = outputDelegate.getOutputMetadataDescriptor(context, keyValue);
    MetadataResult<InputMetadataDescriptor> input = inputDelegate.getInputMetadataDescriptors(context, keyValue);

    if (output.isSuccess() && input.isSuccess()) {
      MetadataAttributes metadataAttributes = getMetadataAttributes(attributesBuilder, outputDelegate, input.get());
      T model = component.getTypedModel(input.get(), output.get());
      return success(ComponentMetadataDescriptor.builder(model).withAttributes(metadataAttributes).build());
    }

    List<MetadataFailure> failures = ImmutableList.<MetadataFailure>builder()
        .addAll(output.getFailures())
        .addAll(input.getFailures())
        .build();

    return failure(ComponentMetadataDescriptor.builder(component).build(), failures);
  }

  private MetadataAttributes getMetadataAttributes(MetadataAttributes.MetadataAttributesBuilder attributesBuilder,
                                                   MetadataOutputDelegate outputDelegate,
                                                   InputMetadataDescriptor input) {

    outputDelegate.getCategoryName().ifPresent(attributesBuilder::withCategoryName);
    outputDelegate.getOutputResolver().ifPresent(r -> attributesBuilder.withOutputResolver(r.getResolverName()));
    outputDelegate.getOutputAttributesResolver()
        .ifPresent(r -> attributesBuilder.withOutputAttributesResolver(r.getResolverName()));
    input.getAllParameters().entrySet()
        .forEach(entry -> attributesBuilder.withParameterResolver(entry.getKey(),
                                                                  inputDelegate.getParameterResolver(entry.getKey())
                                                                      .getResolverName()));
    return attributesBuilder.build();
  }

  private MetadataResult<Object> getMetadataKeyObjectValue(ParameterValueResolver metadataKeyResolver) {
    try {
      Object keyValue = getContainerName().isPresent() ? metadataKeyResolver.getParameterValue(getContainerName().get()) : null;
      return success(keyValue);
    } catch (Exception e) {
      return failure(newFailure(e).onComponent());
    }
  }

  private List<ParameterModel> getMetadataKeyParts(ComponentModel componentModel) {
    return componentModel.getAllParameterModels().stream()
        .filter(p -> p.getModelProperty(MetadataKeyPartModelProperty.class).isPresent())
        .collect(toList());
  }

  private Optional<String> getContainerName() {
    return ofNullable(keyContainerName);
  }
}

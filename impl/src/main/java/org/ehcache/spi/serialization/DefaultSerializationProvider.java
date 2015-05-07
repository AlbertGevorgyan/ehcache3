/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.spi.serialization;

import org.ehcache.config.SerializationProviderConfiguration;
import org.ehcache.config.serializer.DefaultSerializationProviderConfiguration;
import org.ehcache.config.serializer.DefaultSerializationProviderFactoryConfiguration;
import org.ehcache.internal.classes.ClassInstanceProvider;
import org.ehcache.internal.serialization.JavaSerializer;
import org.ehcache.spi.ServiceLocator;
import org.ehcache.spi.ServiceProvider;
import org.ehcache.spi.service.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * @author Ludovic Orban
 */
public class DefaultSerializationProvider extends ClassInstanceProvider<Serializer<?>> implements SerializationProvider {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultSerializationProvider.class);

  public DefaultSerializationProvider() {
    super(DefaultSerializationProviderFactoryConfiguration.class, (Class) DefaultSerializationProviderConfiguration.class);
  }

  @Override
  public <T> Serializer<T> createKeySerializer(Class<T> clazz, ClassLoader classLoader, ServiceConfiguration<?>... configs) {
    DefaultSerializationProviderConfiguration<T> conf = find(SerializationProviderConfiguration.Type.KEY, configs);
    return createSerializer(clazz, classLoader, conf);
  }

  @Override
  public <T> Serializer<T> createValueSerializer(Class<T> clazz, ClassLoader classLoader, ServiceConfiguration<?>... configs) {
    DefaultSerializationProviderConfiguration<T> conf = find(SerializationProviderConfiguration.Type.VALUE, configs);
    return createSerializer(clazz, classLoader, conf);
  }

  private <T> Serializer<T> createSerializer(Class<T> clazz, ClassLoader classLoader, DefaultSerializationProviderConfiguration<T> config) {
    String alias = (config != null ? null : clazz.getName());
    Serializer<T> serializer = (Serializer<T>) newInstance(alias, config, new Arg<ClassLoader>(ClassLoader.class, classLoader));
    if (serializer == null) {
      throw new IllegalArgumentException("No serializer found for type '" + alias + "'");
    }
    LOG.info("Serializer for <{}> : {}", clazz.getName(), serializer);
    return serializer;
  }

  @Override
  protected Class<? extends Serializer<?>> getPreconfigured(String alias, Arg<?>... ctorArgs) {
    Class<? extends Serializer<?>> direct = preconfiguredLoaders.get(alias);
    if (direct != null) {
      return direct;
    }
    ClassLoader classLoader = (ClassLoader) ctorArgs[0].getVal();
    Class<?> targetSerializedClass;
    try {
      targetSerializedClass = Class.forName(alias, true, classLoader);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalArgumentException("Configured type class '" + alias + "' not found", cnfe);
    }
    for (Map.Entry<String, Class<? extends Serializer<?>>> entry : preconfiguredLoaders.entrySet()) {
      try {
        Class<?> configuredSerializedClass = Class.forName(entry.getKey(), true, classLoader);
        if (configuredSerializedClass.isAssignableFrom(targetSerializedClass)) {
          return entry.getValue();
        }
      } catch (ClassNotFoundException cnfe) {
        throw new IllegalArgumentException("Configured type class '" + entry.getKey() + "' for serializer '" + entry.getValue() + "' not found", cnfe);
      }
    }
    return null;
  }

  @Override
  public void start(ServiceConfiguration<?> config, ServiceProvider serviceProvider) {
    super.start(config, serviceProvider);
    addDefaultSerializer();
  }

  protected void addDefaultSerializer() {
    // add java.io.Serializable at the end of the map if it wasn't already there
    if (!preconfiguredLoaders.containsKey(Serializable.class.getName())) {
      preconfiguredLoaders.put(Serializable.class.getName(), (Class) JavaSerializer.class);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> DefaultSerializationProviderConfiguration<T> find(SerializationProviderConfiguration.Type type, ServiceConfiguration<?>... serviceConfigurations) {
    DefaultSerializationProviderConfiguration<T> result = null;

    Collection<DefaultSerializationProviderConfiguration> serializationProviderConfigurations = ServiceLocator.findAmongst(DefaultSerializationProviderConfiguration.class, (Object[]) serviceConfigurations);
    for (DefaultSerializationProviderConfiguration serializationProviderConfiguration : serializationProviderConfigurations) {
      if (serializationProviderConfiguration.getType() == type) {
        if (result != null) {
          throw new IllegalArgumentException("Duplicate " + type + " serialization provider : " + serializationProviderConfiguration);
        }
        result = serializationProviderConfiguration;
      }
    }

    return result;
  }
}

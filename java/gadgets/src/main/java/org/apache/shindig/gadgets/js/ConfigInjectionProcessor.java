/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.shindig.gadgets.js;

import java.util.Map;

import org.apache.shindig.common.JsonSerializer;
import org.apache.shindig.config.ContainerConfig;
import org.apache.shindig.gadgets.GadgetContext;
import org.apache.shindig.gadgets.RenderingContext;
import org.apache.shindig.gadgets.config.ConfigContributor;
import org.apache.shindig.gadgets.features.FeatureRegistry;
import org.apache.shindig.gadgets.uri.JsUriManager.JsUri;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

public class ConfigInjectionProcessor implements JsProcessor {
  private static final String CONFIG_INIT_ID = "[config-injection]";
  @VisibleForTesting
  static final String GADGETS_FEATURES_KEY = "gadgets.features";

  private final FeatureRegistry registry;
  private final ContainerConfig containerConfig;
  private final Map<String, ConfigContributor> configContributors;
  
  @Inject
  public ConfigInjectionProcessor(
      FeatureRegistry registry,
      ContainerConfig containerConfig,
      Map<String, ConfigContributor> configContributors) {
    this.registry = registry;
    this.containerConfig = containerConfig;
    this.configContributors = configContributors;
  }

  public boolean process(JsRequest request, JsResponseBuilder builder)
      throws JsException {
    JsUri jsUri = request.getJsUri();
    GadgetContext ctx = new JsGadgetContext(jsUri);

    // Append gadgets.config initialization if not in standard gadget mode.
    if (ctx.getRenderingContext() != RenderingContext.GADGET) {
      String container = ctx.getContainer();

      // Append some container specific things
      Map<String, Object> features = containerConfig.getMap(container, GADGETS_FEATURES_KEY);

      if (features != null) {
        Map<String, Object> config =
            Maps.newHashMapWithExpectedSize(features.size() + 2);
        
        // Discard what we don't care about.
        for (String name : registry.getFeatures(jsUri.getLibs())) {
          Object conf = features.get(name);
          // Add from containerConfig.
          if (conf != null) {
            config.put(name, conf);
          }
          ConfigContributor contributor = configContributors.get(name);
          if (contributor != null) {
            contributor.contribute(config, container, request.getHost());
          }
        }
        builder.appendJs(
            "gadgets.config.init(" + JsonSerializer.serialize(config) + ");\n", CONFIG_INIT_ID);
      }
    }
    return true;
  }

}
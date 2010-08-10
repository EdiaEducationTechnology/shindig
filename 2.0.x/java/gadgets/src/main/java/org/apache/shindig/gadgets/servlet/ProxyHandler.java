/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets.servlet;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.http.HttpResponseBuilder;
import org.apache.shindig.gadgets.http.RequestPipeline;
import org.apache.shindig.gadgets.rewrite.ResponseRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.RewritingException;
import org.apache.shindig.gadgets.uri.ProxyUriManager;
import org.apache.shindig.gadgets.uri.UriUtils;
import org.apache.shindig.gadgets.uri.UriUtils.DisallowedHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Handles open proxy requests.
 */
@Singleton
public class ProxyHandler {
  // TODO: parameterize these.
  static final Integer LONG_LIVED_REFRESH = (365 * 24 * 60 * 60);  // 1 year
  static final Integer DEFAULT_REFRESH = (60 * 60);                // 1 hour
  
  private final RequestPipeline requestPipeline;
  private final ResponseRewriterRegistry contentRewriterRegistry;

  @Inject
  public ProxyHandler(RequestPipeline requestPipeline,
                      ResponseRewriterRegistry contentRewriterRegistry) {
    this.requestPipeline = requestPipeline;
    this.contentRewriterRegistry = contentRewriterRegistry;
  }

  /**
   * Generate a remote content request based on the parameters sent from the client.
   */
  private HttpRequest buildHttpRequest(
      ProxyUriManager.ProxyUri uriCtx, Uri tgt) throws GadgetException {
    ServletUtil.validateUrl(tgt);
    HttpRequest req = uriCtx.makeHttpRequest(tgt);
    req.setRewriteMimeType(uriCtx.getRewriteMimeType());
    return req;
  }

  public HttpResponse fetch(ProxyUriManager.ProxyUri proxyUri)
      throws IOException, GadgetException {
    HttpRequest rcr = buildHttpRequest(proxyUri, proxyUri.getResource());
    if (rcr == null) {
      throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
          "No url parameter in request", HttpResponse.SC_BAD_REQUEST);      
    }
    
    HttpResponse results = requestPipeline.execute(rcr);
    
    if (results.isError()) {
      // Error: try the fallback. Particularly useful for proxied images.
      Uri fallbackUri = proxyUri.getFallbackUri();
      if (fallbackUri != null) {
        HttpRequest fallbackRcr = buildHttpRequest(proxyUri, fallbackUri);
        results = requestPipeline.execute(fallbackRcr);
      }
    }
    
    if (contentRewriterRegistry != null) {
      try {
        results = contentRewriterRegistry.rewriteHttpResponse(rcr, results);
      } catch (RewritingException e) {
        throw new GadgetException(GadgetException.Code.INTERNAL_SERVER_ERROR, e,
            e.getHttpStatusCode());
      }
    }
    
    HttpResponseBuilder response = new HttpResponseBuilder(results);
    
    try {
      ServletUtil.setCachingHeaders(response,
          proxyUri.translateStatusRefresh(LONG_LIVED_REFRESH, DEFAULT_REFRESH), false);
    } catch (GadgetException gex) {
      return ServletUtil.errorResponse(gex);
    }
    
    UriUtils.copyResponseHeadersAndStatusCode(results, response, true, true,
        DisallowedHeaders.CACHING_DIRECTIVES,  // Proxy sets its own caching headers.
        DisallowedHeaders.CLIENT_STATE_DIRECTIVES,  // Overridden or irrelevant to proxy.
        DisallowedHeaders.OUTPUT_TRANSFER_DIRECTIVES
        );
    
    // Set Content-Type and Content-Disposition. Do this after copy results headers,
    // in order to prevent those from overwriting the correct values.
    setResponseContentHeaders(response, results);
    
    response.setHeader("Content-Type", getContentType(rcr, response));
    
    // TODO: replace this with streaming APIs when ready
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(results.getResponse(), baos);
    response.setResponse(baos.toByteArray());
    return response.create();
  }
  
  private String getContentType(HttpRequest rcr, HttpResponseBuilder results) {
    String contentType = results.getHeader("Content-Type");
    if (!StringUtils.isEmpty(rcr.getRewriteMimeType())) {
      String requiredType = rcr.getRewriteMimeType();
      // Use a 'Vary' style check on the response
      if (requiredType.endsWith("/*") &&
          !StringUtils.isEmpty(contentType)) {
        requiredType = requiredType.substring(0, requiredType.length() - 2);
        if (!contentType.toLowerCase().startsWith(requiredType.toLowerCase())) {
          contentType = requiredType;
        }
      } else {
        contentType = requiredType;
      }
    }
    return contentType;
  }

  private void setResponseContentHeaders(HttpResponseBuilder response, HttpResponse results) {
    // We're skipping the content disposition header for flash due to an issue with Flash player 10
    // This does make some sites a higher value phishing target, but this can be mitigated by
    // additional referer checks.
    if (!"application/x-shockwave-flash".equalsIgnoreCase(results.getHeader("Content-Type")) &&
        !"application/x-shockwave-flash".equalsIgnoreCase(response.getHeader("Content-Type"))) {
      response.setHeader("Content-Disposition", "attachment;filename=p.txt");
    }
    if (results.getHeader("Content-Type") == null) {
      response.setHeader("Content-Type", "application/octet-stream");
    }
  }
}

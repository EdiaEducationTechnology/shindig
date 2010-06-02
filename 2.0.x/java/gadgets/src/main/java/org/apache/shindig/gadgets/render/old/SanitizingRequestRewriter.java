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
package org.apache.shindig.gadgets.render.old;

import org.apache.commons.lang.StringUtils;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSourceInputStream;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponseBuilder;
import org.apache.shindig.gadgets.parse.caja.old.CajaCssSanitizer;
import org.apache.shindig.gadgets.rewrite.ContentRewriterFeature;
import org.apache.shindig.gadgets.rewrite.ResponseRewriter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;

/**
 * Rewriter that sanitizes CSS and image content.
 */
public class SanitizingRequestRewriter implements ResponseRewriter {
  private static final Logger logger =
    Logger.getLogger(SanitizingRequestRewriter.class.getName());

  private final CajaCssSanitizer cssSanitizer;
  private final ContentRewriterFeature.Factory rewriterFeatureFactory;
  private final SanitizingProxyingLinkRewriterFactory sanitizingProxyingLinkRewriterFactory;
  
  @Inject
  public SanitizingRequestRewriter(
      ContentRewriterFeature.Factory rewriterFeatureFactory,
      CajaCssSanitizer cssSanitizer,
      SanitizingProxyingLinkRewriterFactory sanitizingProxyingLinkRewriterFactory) {
    this.cssSanitizer = cssSanitizer;
    this.rewriterFeatureFactory = rewriterFeatureFactory;
    this.sanitizingProxyingLinkRewriterFactory = sanitizingProxyingLinkRewriterFactory;
  }

  public void rewrite(HttpRequest request, HttpResponseBuilder resp) {
    // Content fetched through the proxy can stipulate that it must be sanitized.
    if (request.isSanitizationRequested()) {
      ContentRewriterFeature.Config rewriterFeature =
        rewriterFeatureFactory.createRewriteAllFeature(request.getCacheTtl());
      if (StringUtils.isEmpty(request.getRewriteMimeType())) {
        logger.log(Level.WARNING, "Request to sanitize without content type for "
            + request.getUri());
        resp.setContent("");
      } else if (request.getRewriteMimeType().equalsIgnoreCase("text/css")) {
        rewriteProxiedCss(request, resp, rewriterFeature);
      } else if (request.getRewriteMimeType().toLowerCase().startsWith("image/")) {
        rewriteProxiedImage(request, resp);
      } else {
        logger.log(Level.WARNING, "Request to sanitize unknown content type "
            + request.getRewriteMimeType()
            + " for " + request.getUri());
        resp.setContent("");
      }
    }
  }

  /**
   * We don't actually rewrite the image we just ensure that it is in fact a valid
   * and known image type.
   */
  private void rewriteProxiedImage(HttpRequest request, HttpResponseBuilder resp) {
    boolean imageIsSafe = false;
    try {
      String contentType = resp.getHeader("Content-Type");
      if (contentType == null || contentType.toLowerCase().startsWith("image/")) {
        // Unspecified or unknown image mime type.
        try {
          ImageFormat imageFormat = Sanselan
              .guessFormat(new ByteSourceInputStream(resp.getContentBytes(),
                  request.getUri().getPath()));
          if (imageFormat == ImageFormat.IMAGE_FORMAT_UNKNOWN) {
            logger.log(Level.INFO, "Unable to sanitize unknown image type "
                + request.getUri().toString());
            return;
          }
          imageIsSafe = true;
          // Return false to indicate that no rewriting occurred
          return;
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        } catch (ImageReadException ire) {
          // Unable to read the image so its not safe
          logger.log(Level.INFO, "Unable to detect image type for " +request.getUri().toString() +
              " for sanitized content", ire);
          return;
        }
      } else {
        return;
      }
    } finally {
      if (!imageIsSafe) {
        resp.setContent("");
      }
    }
  }

  /**
   * Sanitize a CSS file.
   */
  private void rewriteProxiedCss(HttpRequest request, HttpResponseBuilder response,
      ContentRewriterFeature.Config rewriterFeature) {
    String sanitized = "";
    try {
      String contentType = response.getHeader("Content-Type");
      if (contentType == null || contentType.toLowerCase().startsWith("text/")) {
        SanitizingProxyingLinkRewriter cssImportRewriter = sanitizingProxyingLinkRewriterFactory
            .create(request.getGadget(), rewriterFeature, request
                .getContainer(), "text/css", false, request.getIgnoreCache());
        SanitizingProxyingLinkRewriter cssImageRewriter = sanitizingProxyingLinkRewriterFactory
            .create(request.getGadget(), rewriterFeature, request
                .getContainer(), "image/*", false, request.getIgnoreCache());
        sanitized = cssSanitizer.sanitize(response.getContent(), request.getUri(),
            cssImportRewriter, cssImageRewriter);
      }
    } finally {
      // Set sanitized content in finally to ensure it is always cleared in
      // the case of errors
      response.setContent(sanitized);
    }
  }
}

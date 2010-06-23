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
package org.apache.shindig.gadgets.servlet;

import com.google.inject.Inject;

import org.apache.shindig.common.servlet.InjectedServlet;
import org.apache.shindig.gadgets.GadgetContext;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles requests for accel servlet.
 * The objective is to accelerate web pages.
 */
public class HtmlAccelServlet extends InjectedServlet {

  private AccelHandler accelHandler;
  private static Logger logger = Logger.getLogger(
      HtmlAccelServlet.class.getName());

  public static final String ACCEL_GADGET_PARAM_NAME = "accelGadget";
  public static final String ACCEL_GADGET_PARAM_VALUE = "true";

  @Inject
  public void setHandler(AccelHandler accelHandler) {
    this.accelHandler = accelHandler;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    logger.fine("accel request = " + request.toString());
    accelHandler.fetch(request, response);
  }

  public static boolean isAccel(GadgetContext context) {
    return context.getParameter(HtmlAccelServlet.ACCEL_GADGET_PARAM_NAME) ==
        HtmlAccelServlet.ACCEL_GADGET_PARAM_VALUE;
  }
}

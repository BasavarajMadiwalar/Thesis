/*
 * Copyright (c) 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.I4application.impl.utils;

/**
 * Describes an exception that is raised during BitBufferHelper operations.
 */
public class BufferException extends Exception{
  private static final long serialVersionUID = 1L;

  public BufferException(String message) {
    super(message);
  }
}

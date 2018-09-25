/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.I4application.cli.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.I4application.cli.api.I4applicationCliCommands;

public class I4applicationCliCommandsImpl implements I4applicationCliCommands {

    private static final Logger LOG = LoggerFactory.getLogger(I4applicationCliCommandsImpl.class);
    private final DataBroker dataBroker;

    public I4applicationCliCommandsImpl(final DataBroker db) {
        this.dataBroker = db;
        LOG.info("I4applicationCliCommandImpl initialized");
    }

    @Override
    public Object testCommand(Object testArgument) {
        return "This is a test implementation of test-command";
    }
}
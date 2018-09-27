/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.I4application.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I4applicationProvider implements BindingAwareConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(I4applicationProvider.class);

    private DataBroker dataBroker;
    private NotificationService notificationService;

    public I4applicationProvider(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onSessionInitialized(BindingAwareBroker.ConsumerContext session) {
        System.out.println("Session Initiated");
        this.notificationService = session.getSALService(NotificationService.class);
        mDNSParserImpl mDNSParser = new mDNSParserImpl(notificationService);
        //InitialFlowExecutor initialFlowExecutor = new InitialFlowExecutor(notificationService);
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("I4applicationProvider Session Initiated");

    }


    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("I4applicationProvider Closed");
    }
}
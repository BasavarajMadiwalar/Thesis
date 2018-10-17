/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentified;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentifiedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.UrlNotificationListener;

public class UrlNotificationHandler implements UrlNotificationListener {

    /**
     *  Create Reference for Notification Service and register this class
     *  as Listener to MD-SAL. So that, MD-SAL notifies on urlpublish events
     *
     */

    private NotificationService notificationService;
    private NotificationPublishService notificationPublishService;

    private Ipv4Address coordinatorAddress = Ipv4Address.getDefaultInstance("10.0.0.3");
    private Ipv4Address opcua_server_Address;

    public UrlNotificationHandler(NotificationService notificationService, NotificationPublishService notificationPublishService) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void onDiscoveryUrlNotification(DiscoveryUrlNotification notification) {
        System.out.println("Evalaute coordinator device");
        opcua_server_Address = Ipv4Address.getDefaultInstance("10.0.0.1");
        publishCoordinator(opcua_server_Address);
    }

    public void publishCoordinator(Ipv4Address server_Address){
        CoOrdinatorIdentified coOrdinatorIdentified = new CoOrdinatorIdentifiedBuilder()
                    .setCoOrdinatorAddress(coordinatorAddress).setOpcuaServerAddress(server_Address).build();

        notificationPublishService.offerNotification(coOrdinatorIdentified);
    }

}

/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_1_0.jms.impl.QueueImpl;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentified;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentifiedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.UrlNotificationListener;

import javax.jms.*;
import javax.naming.NamingException;

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

    Session session;
    MessageProducer messageProducer;


    public UrlNotificationHandler(NotificationService notificationService, NotificationPublishService notificationPublishService) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.notificationPublishService = notificationPublishService;
        try {
            setJMSclient();
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    public void setJMSclient() throws NamingException, JMSException {

        //Create a connection using factory

//        Hashtable<Object, Object> env = new Hashtable<Object, Object>();
//        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
//        env.put("connectionfactory.factoryLookup", "amqp://localhost:5672");
//
//        javax.naming.Context context = new javax.naming.InitialContext(env);
//
//        ConnectionFactory factory = (ConnectionFactory) context.lookup("factoryLookup");

        ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", 5672, "admin", "password");

        Connection connection = factory.createConnection("admin", "password");
        connection.start();

        // Create Session object using connection
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination queue = new QueueImpl("queue");
        //Destination queue = session.createQueue("queue");
        // Create a Producer
        messageProducer = session.createProducer(queue);
    }

    public void publishUrl(DiscoveryUrlNotification notification) throws JMSException {

        // Create a map object to map i[ to url
        MapMessage ip2urlMap = session.createMapMessage();
        ip2urlMap.setString(notification.getSrcIPAddress().toString(), notification.getDiscoveryUrl());

        messageProducer.send(ip2urlMap, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
    }

    @Override
    public void onDiscoveryUrlNotification(DiscoveryUrlNotification notification) {
        System.out.println("Evalaute coordinator device");
        opcua_server_Address = notification.getSrcIPAddress();

//        try {
//            setJMSclient();
//        } catch (NamingException e) {
//            e.printStackTrace();
//        } catch (JMSException e) {
//            e.printStackTrace();
//        }

        try {
            publishUrl(notification);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        publishCoordinator(opcua_server_Address);

    }

    public void publishCoordinator(Ipv4Address server_Address){
        CoOrdinatorIdentified coOrdinatorIdentified = new CoOrdinatorIdentifiedBuilder()
                    .setCoOrdinatorAddress(coordinatorAddress).setOpcuaServerAddress(server_Address).build();

        notificationPublishService.offerNotification(coOrdinatorIdentified);
    }

}

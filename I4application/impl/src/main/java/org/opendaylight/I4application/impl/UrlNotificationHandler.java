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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashMap;

public class UrlNotificationHandler implements UrlNotificationListener {

    /**
     *  Create Reference for Notification Service and register this class
     *  as Listener to MD-SAL. So that, MD-SAL notifies on urlpublish events
     *
     */
    private final static Logger LOG = LoggerFactory.getLogger(UrlNotificationHandler.class);

    private NotificationService notificationService;
    private NotificationPublishService notificationPublishService;

    //private Ipv4Address coordinatorAddress = Ipv4Address.getDefaultInstance("10.0.0.3");
    private Ipv4Address opcua_server_Address;
    public HashMap<String, Ipv4Address> ipRecord = new HashMap<String, Ipv4Address>();
    public ArrayList<Ipv4Address> gripperList = new ArrayList<Ipv4Address>(){{
       add(Ipv4Address.getDefaultInstance("10.0.0.3"));
    }};

    public ArrayList<Ipv4Address> conveyerList = new ArrayList<Ipv4Address>(){{
        add(Ipv4Address.getDefaultInstance("10.0.0.4"));
    }};
    public HashMap<String, ArrayList<Ipv4Address>> skillMap = new HashMap<String, ArrayList<Ipv4Address>>(){{
        put("Gripper", gripperList);
        put("Conveyer", conveyerList);
    }};

    private Session session;
    private MessageProducer messageProducer;
    private Destination opcQueue;
    private TemporaryQueue temporaryQueue;
    private MessageConsumer messageConsumer;


    public UrlNotificationHandler(NotificationService notificationService,
                                  NotificationPublishService notificationPublishService) {
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
        ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", 5672, "admin", "password");
        Connection connection = factory.createConnection("admin", "password");
        connection.start();

        // Create Session object using connection
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        opcQueue = new QueueImpl("queue");
        temporaryQueue = session.createTemporaryQueue();

        // Create a Producer
        messageProducer = session.createProducer(opcQueue);

        // Create a Consumer
        messageConsumer = session.createConsumer(temporaryQueue);
        amqpMessageListener amqpMessageListener = new amqpMessageListener();
        messageConsumer.setMessageListener(amqpMessageListener);
    }

    public void publishUrl(DiscoveryUrlNotification notification) throws JMSException {

        // Create a map object to map i[ to url
        MapMessage ip2urlMap = session.createMapMessage();
        ip2urlMap.setString("IPAddr", notification.getSrcIPAddress().toString());
        ip2urlMap.setString("URL", notification.getDiscoveryUrl());


        ip2urlMap.setJMSReplyTo(temporaryQueue);
        messageProducer.send(ip2urlMap, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
    }

    @Override
    public void onDiscoveryUrlNotification(DiscoveryUrlNotification notification) {
        System.out.println("Publish the url onto queue");
        opcua_server_Address = notification.getSrcIPAddress();

        try {
            ipRecord.put(notification.getSrcIPAddress().toString(), opcua_server_Address);
        } catch (Exception e){
            e.printStackTrace();
        }

        try {
            publishUrl(notification);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void publishCoordinator(Ipv4Address server_Address, String skill){

        ArrayList<Ipv4Address> addrList = skillMap.get(skill);
        for (Ipv4Address coordinatorAddress : addrList){
            CoOrdinatorIdentified coOrdinatorIdentified = new CoOrdinatorIdentifiedBuilder()
                    .setCoOrdinatorAddress(coordinatorAddress).setOpcuaServerAddress(server_Address).build();
            notificationPublishService.offerNotification(coOrdinatorIdentified);
        }
    }


    /**
     * Used to listen for the messages from Activemq queue
     */

    public class amqpMessageListener implements MessageListener {
        Byte[] bytes = null;
        String Skill;
        Ipv4Address opc_ser_Addr = null;
        @Override
        public void onMessage(Message message) {
            Skill = null;
            opc_ser_Addr = null;
            org.apache.qpid.amqp_1_0.jms.MapMessage recievedMessage = (org.apache.qpid.amqp_1_0.jms.MapMessage) message;
            HashMap<String, String> mapMessage = null;
            try {
                String IP_Addr = recievedMessage.getObject("IPAddr").toString();
                opc_ser_Addr = ipRecord.get(IP_Addr);
                Skill = recievedMessage.get("Skill").toString();
            } catch (JMSException e) {
                e.printStackTrace();
            }
            UrlNotificationHandler.this.publishCoordinator(opc_ser_Addr,Skill);
        }
    }
}

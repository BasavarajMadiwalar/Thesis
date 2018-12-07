/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_1_0.jms.impl.QueueImpl;
import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostAddedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostNotificationListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostRemovedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentified;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentifiedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.updateskills.rev181201.UpdateSkillsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.UrlNotificationListener;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Future;

public class UrlNotificationHandler implements UrlNotificationListener, HostNotificationListener, UpdateSkillsService {

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
    private HashMap<String, Ipv4Address> ipRecord = new HashMap<String, Ipv4Address>();
    private HashMap<String,ArrayList<Ipv4Address>> switchMap = new HashMap<String, ArrayList<Ipv4Address>>();

    private HashMap<String, ArrayList<String>> coordinatorskillMap = null;

    private Session session;
    private MessageProducer messageProducer;
    private Destination opcQueue;
    private TemporaryQueue temporaryQueue;
    private MessageConsumer messageConsumer;

    private HostManager hostManager;
    private RpcProviderRegistry rpcProviderRegistry;

    public UrlNotificationHandler(NotificationService notificationService,
                                  NotificationPublishService notificationPublishService
                                  , HostManager hostManager, RpcProviderRegistry rpcProviderRegistry) {
        LOG.info("URL Notification Handler Initiated");
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.notificationPublishService = notificationPublishService;
        this.hostManager = hostManager;
        rpcProviderRegistry.addRpcImplementation(UpdateSkillsService.class, this);

        try {
            setJMSclient();
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }

        try {
            JsontoHashMap();
        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
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

    public void JsontoHashMap() throws JsonGenerationException, JsonMappingException {

        ObjectMapper objectMapper = new ObjectMapper();
        File jsonskillmap = new File("/home/basavaraj/ODL/Thesis/I4application/impl/src/main/resources/skillmap.json");

        try {
             coordinatorskillMap = objectMapper.readValue(jsonskillmap,
                    new TypeReference<HashMap<String, ArrayList<String>>>() {
                    });

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onHostRemovedNotification(HostRemovedNotification notification) {
        LOG.debug("Host Removed Notification Received");
        // Update the Switch to Coordinator Hashmap
        if (switchMap.containsValue(notification.getIPAddress())){
            for (String switchId: switchMap.keySet()){
                if(switchMap.get(switchId).equals(notification.getIPAddress())){
                    try {
                        switchMap.remove(switchId);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onHostAddedNotification(HostAddedNotification notification) {
        LOG.debug("Host Added notification received");
        Ipv4Address hostAddress = notification.getIPAddress();

        // check if the coordinator skillmap as hostaddress as key.
        if (!(coordinatorskillMap.containsKey(hostAddress.getValue()))){
            return;
        }

        try {
            switchMap.put(notification.getSwitchId(), new ArrayList<>(Arrays.asList(hostAddress)));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void publishUrl(DiscoveryUrlNotification notification) throws JMSException {

        // Create a map object to map ip Addr to url
        MapMessage ip2urlMap = session.createMapMessage();
        ip2urlMap.setString("IPAddr", notification.getSrcIPAddress().toString());
        ip2urlMap.setString("URL", notification.getDiscoveryUrl());


        ip2urlMap.setJMSReplyTo(temporaryQueue);
        LOG.debug("Publishing discovery URL {} onto queue", notification.getDiscoveryUrl());
        messageProducer.send(ip2urlMap, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
    }

    @Override
    public void onDiscoveryUrlNotification(DiscoveryUrlNotification notification) {
        LOG.debug("Got discovery URL {} notification",notification.getDiscoveryUrl());
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

    public void publishCoordinator(Ipv4Address server_Address, String skill) {

//        ArrayList<String> addrList = coordinatorskillMap.get(skill);
//        for (String coordinatorAddress : addrList){
//            LOG.info("Publishing identified coordinator {} ", coordinatorAddress);
//            CoOrdinatorIdentified coOrdinatorIdentified = new CoOrdinatorIdentifiedBuilder()
//                    .setCoOrdinatorAddress(Ipv4Address.getDefaultInstance(coordinatorAddress)).setOpcuaServerAddress(server_Address).build();
//            notificationPublishService.offerNotification(coOrdinatorIdentified);
//        }

        // Get the node id of corresponding to opcua-server
        String switchId = hostManager.getIpNode(server_Address).getId().toString();

        // from the switchMap, get the list of cooridnator for node id obtained in previous step
        ArrayList<Ipv4Address> coordinatorList = switchMap.get(switchId);

        // for each coordinator in list, get list of skills in coordinatorskill map. If the skill map consists of obtained skill,
        // then send coordinator corresponding to it.

        for (Ipv4Address coordinator: coordinatorList){
            if (coordinatorskillMap.get(coordinator.getValue()).contains(skill)){
                LOG.info("Publishing identified coordinator {} ", coordinator.getValue());
                System.out.format("Coordinator for %s is %s.%n", server_Address.getValue(),coordinator.getValue());
                CoOrdinatorIdentified coOrdinatorIdentified = new CoOrdinatorIdentifiedBuilder()
                        .setCoOrdinatorAddress(coordinator).setOpcuaServerAddress(server_Address)
                        .build();
                notificationPublishService.offerNotification(coOrdinatorIdentified);
            }
        }
    }

    @Override
    public Future<RpcResult<Void>> updateSkillsMap() {
        System.out.println("Called Update Skill Map");

        try {
            JsontoHashMap();
        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        }

        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
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

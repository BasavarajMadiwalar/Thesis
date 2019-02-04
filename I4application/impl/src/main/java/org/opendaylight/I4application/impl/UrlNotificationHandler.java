/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Future;

public class UrlNotificationHandler implements UrlNotificationListener, HostNotificationListener, UpdateSkillsService {

    /**
     *  Object of the class listens for discovery URL of OPC UA server and publishes them on
     *  to queue on AMQP message broker. Additionally, it listens for response messages from OPC UA Client
     *
     */
    private final static Logger LOG = LoggerFactory.getLogger(UrlNotificationHandler.class);
    private HostManager hostManager;
    private NotificationPublishService notificationPublishService;


    private HashMap<String, Ipv4Address> ipRecord = new HashMap<>();
    private HashMap<String, Ipv4Address> coordinator_ws_map = new HashMap<>();
    private HashMap<String, String> switch_workstation_map = new HashMap<>();
    private HashMap<String, ArrayList<String>> workstation_skillMap = new HashMap<>();

    // Variable related to AMQP message broker connection
    private Connection connection;
    private Session session;
    private MessageProducer url_publisher;
    private Destination opcua_client_queue;
    private TemporaryQueue response_queue;
    private MessageConsumer response_consumer;

    // Json Path variables
    private String skill_map_path = "/home/basavaraj/ODL/Thesis/I4application/impl/src/main/resources/skillmap.json";
    private String switch_ws_path = "/home/basavaraj/ODL/Thesis/I4application/impl/src/main/resources/switch_workstation_map.json";


    public UrlNotificationHandler(NotificationService notificationService, NotificationPublishService notificationPublishService
            , HostManager hostManager, RpcProviderRegistry rpcProviderRegistry) {

        LOG.info("URL Notification Handler Initiated");
        notificationService.registerNotificationListener(this);
        this.notificationPublishService = notificationPublishService;
        this.hostManager = hostManager;
        rpcProviderRegistry.addRpcImplementation(UpdateSkillsService.class, this);

        try {
            create_amqp_client();
        } catch (JMSException e) {
            e.printStackTrace();
        }

        set_skill_map();
        set_switch_workstation_map();
    }

    private void create_amqp_client() throws JMSException {

        //Create a connection using factory
        ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", 5672, "admin", "password");
        connection = factory.createConnection("admin", "password");
        connection.start();

        // Creates session and queue to put discovery url for opcua client
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        opcua_client_queue = new QueueImpl("queue");
        response_queue = session.createTemporaryQueue();

        // Creates a Producer
        url_publisher = session.createProducer(opcua_client_queue);


        // Creates a Consumer and response queue listener object
        response_consumer = session.createConsumer(response_queue);
        response_consumer.setMessageListener(new amqpMessageListener());
    }

    private void set_skill_map(){

        ObjectMapper objectMapper = new ObjectMapper();
        File jsonskillmap = new File(skill_map_path);
        try {
             workstation_skillMap = objectMapper.readValue(jsonskillmap, new TypeReference<HashMap<String, ArrayList<String>>>() {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void set_switch_workstation_map(){

        ObjectMapper objectMapper = new ObjectMapper();
        File jsonskillmap = new File(switch_ws_path);
        try {
            switch_workstation_map = objectMapper.readValue(jsonskillmap,
                    new TypeReference<HashMap<String, String>>() {
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDiscoveryUrlNotification(DiscoveryUrlNotification notification) {
        LOG.debug("Got discovery URL {} notification",notification.getDiscoveryUrl());
        Ipv4Address opcua_server_Address = notification.getSrcIPAddress();

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

    private void publishUrl(DiscoveryUrlNotification notification) throws JMSException {

        // Create a map object to map ip addr to url
        MapMessage ip2urlMap = session.createMapMessage();
        ip2urlMap.setString("IPAddr", notification.getSrcIPAddress().toString());
        ip2urlMap.setString("URL", notification.getDiscoveryUrl());

        ip2urlMap.setJMSReplyTo(response_queue);
        url_publisher.send(ip2urlMap, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
    }

    /**
     * Used to listen for the messages from Activemq queue
     */

    public class amqpMessageListener implements MessageListener {

        String Skill;
        Ipv4Address opc_ser_Addr;
        @Override
        public void onMessage(Message message) {
            LOG.debug("Received skill response");
            org.apache.qpid.amqp_1_0.jms.MapMessage recievedMessage = (org.apache.qpid.amqp_1_0.jms.MapMessage) message;
            try {
                String IP_Addr = recievedMessage.getObject("IPAddr").toString();
                opc_ser_Addr = ipRecord.get(IP_Addr);
                Skill = recievedMessage.get("Skill").toString();
            } catch (JMSException e) {
                e.printStackTrace();
            }
            UrlNotificationHandler.this.identify_coordinator(opc_ser_Addr, Skill);
        }
    }


    private void identify_coordinator(Ipv4Address ipv4Address, String skill){

        if (skill.equals("coordinator")){
            String switch_id = hostManager.getIpNode(ipv4Address).getId().getValue();
            String workstation = switch_workstation_map.get(switch_id);
            coordinator_ws_map.put(workstation, ipv4Address);
            return;
        }

        // Add code to null return from getIpNode
        String switch_id = hostManager.getIpNode(ipv4Address).getId().getValue();
        String workstation = switch_workstation_map.get(switch_id);
        if (workstation_skillMap.get(workstation).contains(skill)){
            Ipv4Address coordinator_ip_addr = coordinator_ws_map.get(workstation);
            LOG.info("Coordinator for {} is {}", ipv4Address.getValue(), coordinator_ip_addr.getValue());
            CoOrdinatorIdentified notification =  new CoOrdinatorIdentifiedBuilder()
                    .setCoOrdinatorAddress(coordinator_ip_addr).setOpcuaServerAddress(ipv4Address)
                    .build();
            notificationPublishService.offerNotification(notification);
        }

    }

    @Override
    public Future<RpcResult<Void>> updateSkillsMap() {
        LOG.info("Update Workstation-skill map");
        set_skill_map();
        set_switch_workstation_map();
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public void onHostRemovedNotification(HostRemovedNotification notification) {
        LOG.debug("Remove Coordinator entry from workstation coordinator map");
        if (coordinator_ws_map.containsValue(notification.getIPAddress())){
            for (String workstation: coordinator_ws_map.keySet()){
                if(coordinator_ws_map.get(workstation).equals(notification.getIPAddress())){
                    try {
                        coordinator_ws_map.remove(workstation);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onHostAddedNotification(HostAddedNotification notification) {
        //Do nothing
    }


}

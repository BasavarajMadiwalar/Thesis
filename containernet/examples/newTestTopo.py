#!/usr/bin/python
"""
This is the most simple example to showcase Containernet.
"""
from mininet.net import Containernet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from time import time
from signal import SIGINT
import subprocess
from time import sleep
import argparse

def createNet(switchcount):

    net = Containernet(controller=None)
    info('*** Adding controller\n')
    net.addController(name="ODL", controller=RemoteController,
                      ip='127.0.0.1', port=6653)

    info('*** Adding switches\n')
    switch_list = []
    for i in range(1, switchcount+1):
        switch_list.append(net.addSwitch('s'+str(i)))

    switch_opcua_map = {}
    ipMap = createIpMap(switchcount)

    # Here we create switch to coordinator map
    info('*** Adding coordinator docker containers\n')
    device_id = 1
    switch_coordinator = {}
    for i in range(1, switchcount+1):
        switch_coordinator['s'+str(i)] = net.addDocker('d'+str(device_id), ip=ipMap['d'+str(device_id)],\
                dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/Testbed/s%d/d%d:/root/opcua' % (i, device_id)])
        device_id=device_id+1

    # Add opc-ua servers to swithc_opcua Map
    info('*** Adding opc-ua server docker containers\n')
    for i in range(1, switchcount+1):
        opcua_servers = []
        for k in range(1, 4):
            opcua_servers.append(net.addDocker('d'+str(device_id), ip=ipMap['d'+str(device_id)],\
                dimage="ubuntu:trusty",volumes=['/home/basavaraj/Th/Testbed/s%d/d%d:/root/opcua' % (i, device_id)]))
            device_id = device_id+1
        switch_opcua_map['s'+str(i)] = opcua_servers

    info('*** Creating links\n')
    switch_id = 1
    for switch in switch_list:
        opcua_list = switch_opcua_map['s'+str(switch_id)]
        for server in opcua_list:
            net.addLink(server,switch)
        net.addLink(server, switch_coordinator['s'+str(switch_id)])
        switch_id=switch_id+1

    length = len(switch_list)

    for i in range(1, length):
        net.addLink(switch_list[i-1], switch_list[i])

    info('*** Starting network\n')
    net.start()

    info('*** Testing connectivity\n')
    hosts_list = net.hosts
    # net.ping(hosts_list)

    info('*** Running script to configure host names')
    for host in hosts_list:
        host.cmd("./root/opcua/hostnamegen.sh 8")

    return net

    # info('*** Running CLI\n')
    # CLI(net)
    # info('*** Stopping network')
    # net.stop()


# Method used to create map of HostName to IP address Map
def createIpMap(switchcount):

    ipMap = {}
    for i in range(1, (switchcount*4)+1):
        ipMap['d'+str(i)] = "10.0.0."+str(i)

    return ipMap


def runProgram(net, switch_count):
    info('*** Collecting Host List')

    # Get list of hosts
    hosts = net.hosts
    opcua_server_count = switch_count*3
    ldsservers = hosts[0:switch_count]
    opcua = hosts[switch_count:opcua_server_count+1]

    popens1 = {}
    popens2 = {}
    endtime = time() + 30

    for lds in ldsservers:
        popens1[lds] = lds.popen(['./root/opcua/ldsserver'],shell=False)

    sleep(5)

    for opcua_server in opcua:
        popens2[opcua_server] = opcua_server.popen(["./root/opcua/server_multicast1"])
        sleep(1)

    info("Monitoring the output for", 10 , "seconds\n")

    for h, line in pmonitor(popens2, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime:
            for p in popens2.values():
                p.send_signal(SIGINT)

    for h, line in pmonitor(popens1, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime:
            for p in popens1.values():
                p.send_signal(SIGINT)


    info('*** Running CLI\n')
    CLI(net)
    info('*** Stopping network')
    net.stop()


if __name__== "__main__":
    setLogLevel('info')
    parser = argparse.ArgumentParser(description="Test Script to generate mininet topology")
    parser.add_argument('-sc', '--switches', help="Switch count for topology", default=2)
    args= parser.parse_args()

    # createIpMap(3)
    net = createNet(args.switches)
    runProgram(net, args.switches)





#!/usr/bin/python
"""
This is the base script for containernet topology generation.

"""
from mininet.net import Containernet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from time import time
from signal import SIGINT, SIGTERM
import subprocess
from time import sleep
import argparse
import json
import os
from shutil import copytree, copyfile, rmtree
import requests

def createNet(switchcount, ipMap):


    net = Containernet(controller=None)
    info('*** Adding controller\n')
    net.addController(name="ODL", controller=RemoteController,
                      ip='127.0.0.1', port=6653)

    info('*** Adding switches\n')
    switch_list = []
    for i in range(1, switchcount+1):
        switch_list.append(net.addSwitch('s'+str(i)))

    switch_opcua_map = {}
    macMap = createMacMap(switchcount)

    # Here we create switch to coordinator map
    info('*** Adding coordinator docker containers\n')
    device_id = 1
    switch_coordinator = {}
    for i in range(1, switchcount+1):
        switch_coordinator['s'+str(i)] = net.addDocker('d'+str(device_id), ip=ipMap['d'+str(device_id)],mac=macMap['d'+str(device_id)],\
                dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/Testbed/s%d/d%d:/root/opcua' % (i, device_id)])
        device_id=device_id+1

    # Add opc-ua servers to switch_opcua Map
    info('*** Adding opc-ua server docker containers\n')
    for i in range(1, switchcount+1):
        opcua_servers = []
        for k in range(1, 4):
            opcua_servers.append(net.addDocker('d'+str(device_id), ip=ipMap['d'+str(device_id)],mac=macMap['d'+str(device_id)],\
                dimage="ubuntu:trusty",volumes=['/home/basavaraj/Th/Testbed/s%d/d%d:/root/opcua' % (i, device_id)]))
            device_id = device_id+1
        switch_opcua_map['s'+str(i)] = opcua_servers

    info('*** Creating links\n')
    switch_id = 1
    for switch in switch_list:
        opcua_list = switch_opcua_map['s'+str(switch_id)]
        for server in opcua_list:
            net.addLink(server,switch)
        net.addLink(switch, switch_coordinator['s'+str(switch_id)])
        switch_id=switch_id+1

    length = len(switch_list)

    for i in range(1, length):
        net.addLink(switch_list[i-1], switch_list[i])

    info('*** Starting network\n')
    net.start()
    hosts_list = net.hosts

    info('*** Running script to configure host names\n')
    for host in hosts_list:
        host.cmd("./root/opcua/hostnamegen.sh %d" %device_id)
        sleep(0.5)


    info('*** Testing connectivity\n')
    for host_id in range(1, len(hosts_list)):
        net.ping([hosts_list[0], hosts_list[host_id]])

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

def createMacMap(switchcount):
    base_mac = "00:00:00:00:00:"
    macMap = {}

    for id in range(1, (switchcount*4)+1):
        if(id>15):
            macMap['d'+str(id)] = base_mac+hex(id).lstrip("0x")
        else:
            macMap['d'+str(id)] = base_mac+'0'+hex(id).lstrip("0x")

    return macMap


def runProgram(net, switch_count):
    info('*** Collecting Host List\n')

    # Get list of hosts
    hosts = net.hosts
    opcua_server_count = switch_count*3
    ldsservers = hosts[0:switch_count]
    opcua = hosts[switch_count:opcua_server_count+switch_count]

    popens1 = {}
    popens2 = {}

    for lds in ldsservers:
        popens1[lds] = lds.popen(['./root/opcua/ldsserver'],shell=False)
        sleep(0.5)

    sleep(3)

    for opcua_server in opcua:
        popens2[opcua_server] = opcua_server.popen(["./root/opcua/server_multicast1"])

    endtime = time() + 40
    endtime1 = time() + 45
    info("Monitoring the output for", 45 , "seconds\n")

    for h, line in pmonitor(popens2, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime:
            for p in popens2.values():
                p.send_signal(SIGINT)

    for h, line in pmonitor(popens1, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime1:
            for p in popens1.values():
                p.send_signal(SIGINT)


    # info('*** Running CLI\n')
    # CLI(net)
    # info('*** Stopping network')
    # net.stop()

def runOPCUA_server(net, switch_count):
    info("**** Running OPC-UA server applications")

    # Get list of hosts
    hosts = net.hosts
    opcua_server_count = switch_count * 3
    opcua = hosts[switch_count:opcua_server_count + switch_count]

    popens2 = {}

    for opcua_server in opcua:
        popens2[opcua_server] = opcua_server.popen(["./root/opcua/server_multicast1"])

    endtime = time() + 35
    info("Monitoring the output for", 40, "seconds\n")

    for h, line in pmonitor(popens2, timeoutms=250):
        if h:
            info('<%s>: %s' % (h.name, line))

        if time() >= endtime:
            for p in popens2.values():
                p.send_signal(SIGINT)


def stopNet(net):
    info("**** Stopping Mininet Network")

    net.stop()


def createSkillMap(switch_count,IpMap):

    info("*-*- Creating coordinator Skill Map\n")

    skill_list = ["Gripper", "Conveyer", "Sensor"]
    skill_map = {}

    for switch_id in range(1, switch_count+1):
        skill_map[IpMap['d'+str(switch_id)]] = skill_list

    with open('../../I4application/impl/src/main/resources/skillmap.json', 'w') as file:
        json.dump(skill_map, file, sort_keys=True, indent=4)

def create_coordinatorList(switch_count, IpMap):
    info("*-*- Creating coordinator list\n")

    coordinator_list = []
    coordinator_map = {}

    for i in range(1, switch_count+1):
        coordinator_list.append(IpMap['d'+str(i)])

    coordinator_map["coordinators"] = coordinator_list

    with open('../../I4application/impl/src/main/resources/coordinatorList.json', 'w') as file:
        json.dump(coordinator_map, file, sort_keys=True, indent=4)

def updateHostnames(IpMap):
    info("*** Updating hostnames in Local Hosts\n")

    with open('/etc/hosts') as oldfile:
        with open('/etc/newhosts', 'w+') as newfile:
            for line in oldfile:
                if '10.0.0.' in line:
                    continue
                else:
                    newfile.write(line)


    with open('/etc/newhosts', 'a') as newfile:
        for key,value in IpMap.items():
            newfile.write("%s  %s\n"%(value, key))

    os.remove('/etc/hosts')
    os.rename('/etc/newhosts', '/etc/hosts')

def updatefolders(switch_count):
    skill_list = ['Gripper', 'Conveyer', 'Sensor']

    # Create first level folder for switches
    for switch_id in range(1, switch_count + 1):
        os.makedirs('/home/basavaraj/Th/Testbed/s' + str(switch_id), mode=0o777)

    device_id = 1
    for switch_id in range(1, switch_count + 1):
        copytree('/home/basavaraj/pythontest/coord', '/home/basavaraj/Th/Testbed/s%d/d%d' % (switch_id, device_id))
        device_id += 1

    for switch_id in range(1, switch_count + 1):
        copytree('/home/basavaraj/pythontest/Gripper', '/home/basavaraj/Th/Testbed/s%d/d%d' % (switch_id, device_id))
        copytree('/home/basavaraj/pythontest/Conveyer', '/home/basavaraj/Th/Testbed/s%d/d%d' % (switch_id, device_id + 1))
        copytree('/home/basavaraj/pythontest/Sensor', '/home/basavaraj/Th/Testbed/s%d/d%d' % (switch_id, device_id + 2))
        device_id += 3

def copyTimeRecords(switch_count, topology_id):
    info("**** Copying timestamp records to Results folder\n")
    device_id = switch_count+1

    # Create the directory first, as shutil copy throws an error if directory doesn't exist
    # Then copy the files
    for switch_id in range(1, switch_count+1):
        os.makedirs('/home/basavaraj/ODL/Thesis/results/%s/d%d'%(topology_id, device_id))
        copyfile('/home/basavaraj/Th/Testbed/s%d/d%d/timestamp.txt'%(switch_id, device_id), \
                 '/home/basavaraj/ODL/Thesis/results/%s/d%d/timestamp.txt'%(topology_id, device_id))

        os.makedirs('/home/basavaraj/ODL/Thesis/results/%s/d%d' % (topology_id, device_id + 1))
        copyfile('/home/basavaraj/Th/Testbed/s%d/d%d/timestamp.txt' % (switch_id, device_id+1),\
                 '/home/basavaraj/ODL/Thesis/results/%s/d%d/timestamp.txt' % (topology_id, device_id+1))

        os.makedirs('/home/basavaraj/ODL/Thesis/results/%s/d%d' % (topology_id, device_id + 2))
        copyfile('/home/basavaraj/Th/Testbed/s%d/d%d/timestamp.txt' % (switch_id, device_id+2),\
                 '/home/basavaraj/ODL/Thesis/results/%s/d%d/timestamp.txt' % (topology_id, device_id+2))

        device_id += 3

def cleanFolders():
    info("**** Removing Folders in TestBed\n")
    path = '/home/basavaraj/Th/Testbed/'
    rmtree(path)

def makeRpc(url):
    info("**** Making an RPC Call to update SkillMap\n")
    r = requests.post(url, auth=('admin', 'admin'))
    print r
    s =  requests.post('http://localhost:8181/restconf/operations/updateCoordinator:update-coordinator-list', auth=('admin', 'admin'))

def flushPackets(url):
    info("**** Flush stored Packets\n")
    r = requests.post(url, auth=('admin', 'admin'))

def checkStatus(pid):
    poll = pid.poll()
    if(poll==None):
        return True
    else:
        return False

def start_server():
    info("**** Starting amqp server\n")
    arg = ["sudo", "ip", "netns", "exec", "opcuaclient", "./server"]
    proc = subprocess.Popen(arg)
    return proc

def purge_messages():
    info("**** Purge ActiveMQ queue\n")
    subprocess.call(["/home/basavaraj/dev/apache-activemq-5.15.6/bin/activemq", "purge", "queue"])
    sleep(5)


if __name__== "__main__":
    setLogLevel('info')
    parser = argparse.ArgumentParser(description="Test Script to generate mininet topology")
    parser.add_argument('-sc', '--switches', type=int, help="Switch count for topology", default=2)
    args= parser.parse_args()

    url = 'http://localhost:8181/restconf/operations/updateSkills:update-skills-map'
    topo = ['two', 'three', 'four', 'five', 'six', 'seven', 'eight']
    url2 = 'http://localhost:8181/restconf/operations/updateCoordinator:update-coordinator-list'
    url3 = "http://localhost:8181/restconf/operations/flushPktRpc:flushPkts"

    iteration = 50

    start_time = time()
    # makeRpc(url)
    # cleanFolders()

    amqp_server = start_server()

    for switch_count in range (2, args.switches+1):

        updatefolders(switch_count)
        IpMap = createIpMap((switch_count))
        updateHostnames(IpMap)

        # Uncomment below only when topology is changed
        createSkillMap(switch_count, IpMap)
        create_coordinatorList(switch_count, IpMap)
        # Make an RPC call
        makeRpc(url)
        sleep(3)
        net = createNet(switch_count, IpMap)

        while iteration:
            runProgram(net, switch_count)
            flushPackets(url3)
            sleep(5)
            if(not(checkStatus(amqp_server))):
                purge_messages()
                amqp_server = start_server()
            else:
                logfile = open('Testlog', 'a+')
                logfile.write('Topology: %d and iteration: %d \n' % (switch_count, iteration))
                logfile.close()
                iteration -= 1

        stopNet(net)
        copyTimeRecords(switch_count, topo[switch_count-2])
        cleanFolders()
        iteration = 50

    amqp_server.send_signal(SIGTERM)
    duration = time() - start_time

    info("Time to complete Measurement is:" + str(duration))

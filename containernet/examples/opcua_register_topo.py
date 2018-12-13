#!/usr/bin/python
"""
This is the most simple example to showcase Containernet.
"""
from mininet.net import Containernet
from mininet.node import Controller
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from signal import SIGINT
from time import time, sleep
import os
from shutil import  copyfile, copytree, rmtree
import argparse
import subprocess


def create_network(switch_count):
    info("**** Setting up Network\n")
    net = Containernet(controller=None)
    info("**** Adding Switches to \n")
    switch_list = []
    for switch_id in range(1, switch_count+1):
        switch_list.append(net.addSwitch('s'+ str(switch_id)))

    # Create Switch to coordinator map
    switch_coordinator_map = {}
    info("**** Adding coordinator docker containers\n")
    device_id = 1
    for switch_id in range(1, switch_count+1):
        switch_coordinator_map['s'+str(switch_id)] = net.addDocker('d'+str(switch_id),
                ip=ip_map['d'+str(device_id)], mac_map= mac_map['d'+str(device_id)],
                    dimage="ubuntu:trusty", volumes=[testbed_path+'s%d/d%d:/root/opcua'%(switch_id, device_id)])
        device_id+=1

    # Create OPC-UA servers to switch_opcua_map
    info("**** Adding OPC-UA server containers\n")
    switch_opcua_map = {}
    for switch_id in range(1, switch_count+1):
        opcua_servers = []
        for k in range(1, 4):
            opcua_servers.append(net.addDocker('d'+str(device_id), ip=ip_map['d'+str(device_id)],
                  mac=mac_map['d'+str(device_id)], dimage="ubuntu:trusty",
                        volumes=[testbed_path+'s%d/d%d:/root/opcua'%(switch_id,device_id)]))

            device_id+=1
        switch_opcua_map['s'+str(switch_id)] = opcua_servers

    info("**** Creating links\n")
    switch_id = 1
    for switch in switch_list:
        opcua_list = switch_opcua_map['s'+str(switch_id)]
        for server in opcua_list:
            net.addLink(server,switch)
        net.addLink(switch, switch_coordinator_map['s'+str(switch_id)])
        switch_id+=1

    for i in range(1, len(switch_list)):
        net.addLink(switch_list[i-1], switch_list[i])

    info("**** Starting Network\n")
    net.start()
    host_list = net.hosts

    info("**** Running Script to configure host names\n")
    for host in host_list:
        host.cmd("./root/opcua/hostnamegen.sh %d" %device_id)
        sleep(0.25)

    return net

def create_ip_mac_map(switch_count):
    info("**** Creating IP and MAC map\n")

    device_count = switch_count*device_per_switch
    base_mac = "00:00:00:00:00:"
    for i in range(1, device_count+1):
        ip_map['d'+str(i)] = "10.0.0."+str(i)

    for id in range(1, device_count+1):
        if id>15:
            mac_map['d'+str(id)] = base_mac+hex(id).lstrip("0x")
        else:
            mac_map['d'+str(id)] = base_mac+'0'+hex(id).lstrip("0x")


def create_test_folders(switch_count):
    info("**** Setting folders structure for test\n")
    # Create fist level of folders with switch names
    for switch_id in range(1, switch_count+1):
        os.makedirs(testbed_path + 's%d'%(switch_id), mode=0o777)

    device_id = 1
    for switch_id in range(1, switch_count+1):
        copytree(test_folder_path+'coord', testbed_path + 's%d/d%d' %(switch_id, device_id))
        device_id+=1

    for switch_id in range(1, switch_count+1):
        copytree(test_folder_path+skill_list[0], testbed_path+'s%d/d%d' %(switch_id, device_id))
        copytree(test_folder_path+skill_list[1], testbed_path+'s%d/d%d' %(switch_id, device_id+1))
        copytree(test_folder_path+skill_list[2], testbed_path+'s%d/d%d' %(switch_id, device_id+2))
        device_id+=3

def run_program(switch_count):
    info("**** Starting coordinator and opcua servers\n")
    hosts = net.hosts
    opcua_server_count = switch_count * 3
    ldsservers = hosts[0:switch_count]
    opcua_servers = hosts[switch_count:opcua_server_count+switch_count]

    popens1 = {}
    popens2 = {}

    for lds in ldsservers:
        popens1[lds] = lds.popen(['./root/opcua/ldsserver'], shell=False)
        sleep(0.5)
    sleep(3)

    lds_id = 1
    count = 0
    for opcua_server in opcua_servers:
        popens2[opcua_server] =  opcua_server.popen(["/bin/bash", "-c", "./root/opcua/server_register opc.tcp://%s:4840" %(ip_map['d'+str(lds_id)])])
        count+=1
        if count==3:
            lds_id+=1
            count=0

    endtime1 = time()+30
    endtime2 = time()+35

    for h, line in pmonitor(popens2, timeoutms=250):
        if h:
            info('<%s>: %s' % (h.name, line))

        if time() >= endtime1:
            for p in popens2.values():
                p.send_signal(SIGINT)

    for h, line in pmonitor(popens1, timeoutms=250):
        if h:
            info('<%s>: %s' % (h.name, line))

        if time() >= endtime2:
            for p in popens1.values():
                p.send_signal(SIGINT)


def stop_net(net):
    info("**** Stopping Mininet Network\n")
    net.stop()


def clean_folders():
    info("**** Cleaning test folders\n")
    rmtree(testbed_path)


def config_flow(switch_count):
    info("**** Configuraing OVS normal mode flow\n")
    for switch_id in range(1, switch_count+1):
        subprocess.call(["sudo", 'ovs-ofctl', 'add-flow', 's%d'%switch_id, 'action=normal'])

    hosts = net.hosts
    info("Testing Connectivity\n")
    net.ping(hosts)


def copy_time_records(switch_count, topology_id):
    info("**** Copying timestamp records to Results folder\n")

    device_id = switch_count+1

    for switch_id in range(1, switch_count + 1):
        os.makedirs(test_results_path+'%s/d%d' % (topology_id, device_id))
        copyfile(testbed_path+'s%d/d%d/timestamp.txt' % (switch_id, device_id), \
                 test_results_path+'%s/d%d/timestamp.txt' % (topology_id, device_id))

        os.makedirs(test_results_path+'%s/d%d' % (topology_id, device_id + 1))
        copyfile(testbed_path+'s%d/d%d/timestamp.txt' % (switch_id, device_id + 1), \
                 test_results_path+'%s/d%d/timestamp.txt' % (topology_id, device_id + 1))

        os.makedirs(test_results_path+'%s/d%d' % (topology_id, device_id + 2))
        copyfile(testbed_path+'s%d/d%d/timestamp.txt' % (switch_id, device_id + 2), \
                 test_results_path+'%s/d%d/timestamp.txt' % (topology_id, device_id + 2))

        device_id += 3


if __name__ == "__main__":
    setLogLevel('info')
    parser = argparse.ArgumentParser(description="Test Script to generate mininet topology")
    parser.add_argument('-sc', '--switches', type=int, help="Switch count for topology", default=2)
    args = parser.parse_args()


    testbed_path = "/home/basavaraj/Th/opcua_register_folders/Testbed/"
    test_folder_path = "/home/basavaraj/Th/opcua_register_folders/"
    test_results_path = "/home/basavaraj/Th/opcua_register_folders/results/"
    device_per_switch = 4
    ip_map = {}
    mac_map = {}
    skill_list = ['Gripper', 'Conveyer', 'Sensor']
    topo = ['2', '3', '4', '5', '6', '8', '7', '9', '10', '11', '12', '13', '14']

    start_time = time()
    iteration = 30

    for switch_count in range(2, args.switches+1, 2):
        create_test_folders(switch_count)
        create_ip_mac_map(switch_count)
        net = create_network(switch_count)
        config_flow(switch_count)

        while iteration:
            run_program(switch_count)
            sleep(5)
            logfile = open('Testlog', 'a+')
            logfile.write('Topology: %d and iteration: %d \n' % (switch_count, iteration))
            logfile.close()
            iteration-=1

        stop_net(net)
        copy_time_records(switch_count, topo[switch_count-2])
        clean_folders()
        iteration=30

    duration = time() - start_time

    info("Time to complete Measurement is:" + str(duration))
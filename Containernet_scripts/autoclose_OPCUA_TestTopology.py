#!/usr/bin/python
"""
This is the base script for containernet topology generation.

"""
from mininet.net import Containernet
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from time import time
from signal import SIGINT
import subprocess
from time import sleep
import argparse
import json
import os
from shutil import copytree, copyfile, rmtree
import requests
import multiprocessing as mp
import pyshark


def create_net():

    net = Containernet(controller=None)
    info('*** Adding controller\n')
    net.addController(name="ODL", controller=RemoteController,
                      ip='127.0.0.1', port=6653)

    info('*** Adding switches\n')
    switch_list = []
    for i in range(1, switch_count+1):
        switch_list.append(net.addSwitch('s'+str(i)))

    switch_opcua_map = {}

    # Here we create switch to coordinator map
    info('*** Adding coordinator docker containers\n')
    device_id = 1
    switch_coordinator = {}
    for i in range(1, switch_count+1):
        switch_coordinator['s'+str(i)] = net.addDocker('d'+str(device_id), ip=ip_map['d'+str(device_id)],
            mac=mac_map['d'+str(device_id)],
                dimage="ubuntu:trusty", volumes=[test_device_folders+'s%d/d%d:/root/opcua' % (i, device_id)])
        device_id = device_id+1

    # Add opc-ua servers to switch_opcua Map
    info('*** Adding opc-ua server docker containers\n')
    for i in range(1, switch_count+1):
        opcua_servers = []
        for n in range(1, 4):
            opcua_servers.append(net.addDocker('d'+str(device_id), ip=ip_map['d'+str(device_id)],mac=mac_map['d'+str(device_id)],
                dimage="ubuntu:trusty",volumes=[test_device_folders+'s%d/d%d:/root/opcua' % (i, device_id)]))
            device_id = device_id+1
        switch_opcua_map['s'+str(i)] = opcua_servers

    info('*** Creating links\n')
    switch_id = 1
    for switch in switch_list:
        opcua_list = switch_opcua_map['s'+str(switch_id)]
        for server in opcua_list:
            link = net.addLink(server,switch)
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
        sleep(0.25)

    info('*** Testing connectivity\n')
    for host_id in range(1, len(hosts_list)):
        net.ping([hosts_list[0], hosts_list[host_id]])

    return net, link

    # info('*** Running CLI\n')
    # CLI(net)
    # info('*** Stopping network')
    # net.stop()


def create_ip_map():
    info("**** Creating IP Map\n")
    for i in range(1, (switch_count*4)+1):
        ip_map['d'+str(i)] = "10.0.0."+str(i)


def create_mac_map():
    base_mac = "00:00:00:00:00:"

    for k in range(1, (switch_count*4)+1):
        if k > 15:
            mac_map['d'+str(k)] = base_mac+hex(k).lstrip("0x")
        else:
            mac_map['d'+str(k)] = base_mac+'0'+hex(k).lstrip("0x")


def run_program():
    info('*** Collecting Host List\n')

    hosts = net.hosts        # Get list of hosts
    opcua_server_count = switch_count*3
    lds_servers = hosts[0:switch_count]
    opcua = hosts[switch_count:opcua_server_count+switch_count]
    # random.shuffle(opcua)        # Randomize the hosts order to mimic random boot order

    lds_pid_map = {}
    opcua_pid_map = {}

    for lds in lds_servers:
        lds_pid_map[lds] = lds.popen(['./root/opcua/ldsserver'])
        sleep(0.25)
    sleep(3)

    for opcua_server in opcua:
        opcua_pid_map[opcua_server] = opcua_server.popen(["./root/opcua/server_multicast1"])

    end_time_opcua = time() + 40
    end_time_lds = time() + 45

    info("Monitoring the output for", 45, "seconds\n")
    for h, line in pmonitor(opcua_pid_map, timeoutms=250):
        if h:
            info('<%s>: %s' % (h.name, line))

        if time() >= end_time_opcua:
            for p in opcua_pid_map.values():
                p.send_signal(SIGINT)

    for h, line in pmonitor(lds_pid_map, timeoutms=250):
        if h:
            info('<%s>: %s' % (h.name, line))

        if time() >= end_time_lds:
            for p in lds_pid_map.values():
                p.send_signal(SIGINT)


    # info('*** Running CLI\n')
    # CLI(net)
    # info('*** Stopping network')
    # net.stop()


def stop_net(net):
    info("**** Stopping Mininet Network")
    net.stop()


def create_skill_map():

    info("*-*- Creating coordinator Skill Map\n")
    skill_map = {}

    for switch_id in range(1, switch_count+1):
        skill_map["ws:"+str(switch_id)] = skill_list

    with open('../../I4application/impl/src/main/resources/skillmap.json', 'w') as file:
        json.dump(skill_map, file, sort_keys=True, indent=4)


def create_switch_workstation_map():
    info("**** Creating switch_workstation map\n")
    switch_workstation_map = {}

    for switch_id in range(1, switch_count+1):
        switch_workstation_map["openflow:"+str(switch_id)] = "ws:"+str(switch_id)

    with open('../../I4application/impl/src/main/resources/switch_workstation_map.json', 'w') as file:
        json.dump(switch_workstation_map, file, sort_keys=True, indent=4)



def update_hostnames():
    info("*** Updating hostnames in Local Hosts\n")

    with open('/etc/hosts') as oldfile:
        with open('/etc/newhosts', 'w+') as newfile:
            for line in oldfile:
                if '10.0.0.' in line:
                    continue
                else:
                    newfile.write(line)

    with open('/etc/newhosts', 'a') as newfile:
        for key,value in ip_map.items():
            newfile.write("%s  %s\n"%(value, key))

    os.remove('/etc/hosts')
    os.rename('/etc/newhosts', '/etc/hosts')


def update_folders(switch_count):

    for switch_id in range(1, switch_count + 1):            # Create first level folder for switches
        os.makedirs(test_device_folders+'s' + str(switch_id), mode=0o777)

    device_id = 1
    for switch_id in range(1, switch_count + 1):
        copytree(base_folders+'coord', test_device_folders+'s%d/d%d' % (switch_id, device_id))
        device_id += 1

    for switch_id in range(1, switch_count + 1):
        copytree(base_folders+skill_list[0], test_device_folders+'s%d/d%d' % (switch_id, device_id))
        copytree(base_folders+skill_list[1], test_device_folders+'s%d/d%d' % (switch_id, device_id + 1))
        copytree(base_folders+skill_list[2], test_device_folders+'s%d/d%d' % (switch_id, device_id + 2))
        device_id += 3


def copy_time_records(switch_count, topology_id):
    info("**** Copying timestamp records to Results folder\n")
    device_id = switch_count+1

    for switch_id in range(1, switch_count+1):                  # Create the directory first, as shutil copy throws an
        os.makedirs(results_folder+'%s/d%d'%(topology_id, device_id))       # error if directory doesn't exist
        copyfile(test_device_folders+'s%d/d%d/timestamp.txt'%(switch_id, device_id),
                 results_folder+'%s/d%d/timestamp.txt'%(topology_id, device_id))

        os.makedirs(results_folder+'%s/d%d' % (topology_id, device_id + 1))
        copyfile(test_device_folders+'s%d/d%d/timestamp.txt' % (switch_id, device_id+1),
                 results_folder+'%s/d%d/timestamp.txt' % (topology_id, device_id+1))

        os.makedirs(results_folder+'%s/d%d' % (topology_id, device_id + 2))
        copyfile(test_device_folders+'s%d/d%d/timestamp.txt' % (switch_id, device_id+2),
                 results_folder+'%s/d%d/timestamp.txt' % (topology_id, device_id+2))

        device_id += 3


def clean_folders():
    info("**** Removing Folders in TestBed\n")
    rmtree(test_device_folders)


def make_rpc():
    info("**** Making an RPC Call to update SkillMap\n")
    requests.post(url1, auth=('admin', 'admin'))
    r = requests.post(url2, auth=('admin', 'admin'))
    print r


def flush_packets():
    info("**** Flush stored Packets\n")
    requests.post(url3, auth=('admin', 'admin'))


def check_status():
    cnt = 0
    for pid in amqp_server_pid:
        poll = pid.poll()
        if poll is not None:
            amqp_server_pid.remove(pid)
            cnt += 1
        else:
            continue
    return cnt


def start_server(count):
    info("**** Starting amqp server\n")
    arg = ["sudo", "ip", "netns", "exec", "opcuaclient", "./server"]
    for cnt in range(1, count+1):
        amqp_server_pid.append(subprocess.Popen(arg))


def purge_messages():
    info("**** Purge ActiveMQ queue\n")
    subprocess.call(["/home/basavaraj/dev/apache-activemq-5.15.6/bin/activemq", "purge", "queue"])
    sleep(5)


def flush_group_table():
    info("**** Clearing Group Table\n")
    requests.post(url4, auth=('admin', 'admin'))


def listen_packet(ifname):
    info("**** Listening for packets\n")
    cap = pyshark.LiveCapture(interface=str(ifname), bpf_filter='dst host 224.0.0.251')
    cap.sniff(timeout=15)
    packet_count.append(len(cap))


if __name__ == "__main__":

    setLogLevel('info')
    parser = argparse.ArgumentParser(description="Test Script to generate mininet topology")
    parser.add_argument('-sc', '--switches', type=int, help="Switch count for topology", default=2)
    args = parser.parse_args()

    url1 = 'http://localhost:8181/restconf/operations/updateSkills:update-skills-map'
    url2 = 'http://localhost:8181/restconf/operations/updateCoordinator:update-coordinator-list'
    url3 = "http://localhost:8181/restconf/operations/flushPktRpc:flushPkts"
    url4 = 'http://localhost:8181/restconf/operations/FlushGroupTable:flushGrpTable'

    base_folders = "/home/basavaraj/ODL/test_folder/base_folders/"      #holds executables
    test_device_folders = "/home/basavaraj/Th/opcua_mDNS/test_device_folders/"   # holds_test device folders
    results_folder = "/home/basavaraj/Th/opcua_mDNS/results/"       # holds results

    ip_map = {}
    mac_map = {}
    skill_list = ["Gripper", "Conveyer", "Sensor"]
    topology = [k for k in range(2, args.switches+1)]
    packet_count = []


    iteration = 5
    start_time = time()
    amqp_server_pid = []
    count = 12
    start_server(count)

    for switch_count in range(2, args.switches+1, 2):

        update_folders(switch_count)
        create_ip_map()
        create_mac_map()
        update_hostnames()
        create_skill_map()
        create_switch_workstation_map()
        # copy_file()         # Copies coorindator and skill map to SDN controller machine
        make_rpc()          # Make an RPC to ODL to update the Skill Map and flush old packets
        sleep(1)
        net, link = create_net()

        while iteration:
            lp = mp.Process(target=listen_packet, args=(link.intf2,))
            lp.start()
            run_program()
            flush_packets()
            flush_group_table()
            sleep(3)
            lp.join()
            count = check_status()
            if count:
                purge_messages()
                start_server(count)
                iteration -= 1
            else:
                logfile = open('Testlog', 'a+')
                logfile.write('Topology: %d and iteration: %d \n' % (switch_count, iteration))
                logfile.close()
                iteration -= 1

        stop_net(net)
        copy_time_records(switch_count, str(topology[switch_count-2]))
        clean_folders()
        iteration = 5

    # amqp_server.send_signal(SIGTERM)
    duration = time() - start_time

    info("Time to complete Measurement is: %d\n" % duration)

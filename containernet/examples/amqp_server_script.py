#!/usr/bin/python
"""
This is the base script for containernet topology generation.

"""
from mininet.log import info, setLogLevel
import subprocess
from time import sleep
import argparse
import os


def create_ip_map():
    info("**** Creating IP Map\n")
    for i in range(1, (switch_count*4)+1):
        ip_map['d'+str(i)] = "10.0.0."+str(i)


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


if __name__ == "__main__":

    setLogLevel('info')
    parser = argparse.ArgumentParser(description="Test Script to generate mininet topology")
    parser.add_argument('-c', '--count', type=int, help="server count for test", default=8)
    parser.add_argument('-sc', '--switches', type=int, help="switch count for topology", default=2)
    args = parser.parse_args()

    ip_map = {}
    switch_count = args.switches
    amqp_server_pid = []
    count = args.count
    start_server(count)

    while True:
        sleep(2)
        count = check_status()
        if count:
            purge_messages()
            start_server(count)

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include <qpid/messaging/Address.h>
#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include <qpid/types/Variant.h>


#include <algorithm>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <sstream>
#include <proton/container.hpp>
#include <cstring>

extern "C" {
    #include "opcuaclient.h"
}

using namespace qpid::messaging;

using std::stringstream;
using std::string;


void asyncfunction(Message message){
    Message recievedmsg = message;
    const Address& address = recievedmsg.getReplyTo();
    if (address){
        std::cout << "From Async Function" << std::endl;
    }
}

int main(int argc, char** argv) {

    const char* url = argc>1 ? argv[1] : "amqp:tcp:192.168.10.2:5672";
    std::string connectionOptions = argc > 2 ? argv[2] : "{protocol:amqp1.0}";
    const unsigned URLSIZE = 256;
    /*char url_prefix[URLSIZE] = "opc.tcp://";*/
    char *skill;



    Connection connection(url, connectionOptions);
    try {
        connection.open();
        Session session = connection.createSession();
        Receiver receiver = session.createReceiver("queue");


        while (true) {
            Message request = receiver.fetch();
            const Address& address = request.getReplyTo();
            char url_prefix[URLSIZE] = "opc.tcp://";
            asyncfunction(request);
            if (address) {
                Sender sender = session.createSender(address);
                Message response;

                qpid::types::Variant requestObj = request.getContentObject();
                if (requestObj.getType() == qpid::types::VAR_MAP) {

                    qpid::types::Variant::Map map;
                    std::cout << requestObj << std::endl;
                    map=requestObj.asMap();

                    std::string opcua = (std::string)map.operator[]("URL");
                    std::strncat(url_prefix, opcua.c_str(), URLSIZE-1);
                    std::cout<< "The URL sent " << url_prefix << std::endl;
                    skill = readskill(url_prefix);
                    map["Skill"] = skill;

                    qpid::types::Variant responseObj(map);
                    responseObj.setEncoding("string");
                    response.setContentObject(responseObj);
                }
                sender.send(response, true);
                std::cout << "Processed request: "
                          << request.getContentObject()
                          << " -> "
                          << response.getContentObject() << std::endl;
                session.acknowledge();
                sender.close();
            } else {
                std::cerr << "Error: no reply address specified for request: " << request.getContent() << std::endl;
                session.reject(request);
            }
        }
        connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
        connection.close();
    }
    return 1;
}



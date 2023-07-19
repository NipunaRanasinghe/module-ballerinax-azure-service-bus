// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/log;
import ballerinax/azure.sb;

// Connection Configurations
configurable string connectionString = ?;
configurable string queueName = ?;

// This sample demonstrates a scenario where azure service bus connecter is used to 
// send a message to a queue using message sender, receive that message using message receiver with PEEKLOCK mode, 
// abadon that message (discards the message and relinquishes the message lock ownership),
// finally complete the processing of message using complete function of the caller. 
// After this point, we cannot futher perform operations on message.
public function main() returns error? {

    // Input values
    string stringContent = "This is My Message Body";
    byte[] byteContent = stringContent.toBytes();
    int timeToLive = 60; // In seconds
    int serverWaitTime = 60; // In seconds

    sb:ApplicationProperties applicationProperties = {
        properties: {a: "propertyValue1", b: "propertyValue2"}
    };

    sb:Message message1 = {
        body: byteContent,
        contentType: sb:TEXT,
        timeToLive: timeToLive,
        applicationProperties: applicationProperties
    };

    sb:ASBServiceSenderConfig senderConfig = {
        connectionString: connectionString,
        entityType: sb:QUEUE,
        topicOrQueueName: queueName
    };

    sb:ASBServiceReceiverConfig receiverConfig = {
        connectionString: connectionString,
        entityConfig: {
            queueName: queueName
        },
        receiveMode: sb:PEEK_LOCK
    };

    log:printInfo("Initializing Asb sender client.");
    sb:MessageSender queueSender = check new (senderConfig);

    log:printInfo("Initializing Asb receiver client.");
    sb:MessageReceiver queueReceiver = check new (receiverConfig);

    log:printInfo("Sending via Asb sender client.");
    check queueSender->send(message1);

    log:printInfo("Receiving from Asb receiver client.");
    sb:Message|error? messageReceived = queueReceiver->receive(serverWaitTime);

    if (messageReceived is sb:Message) {
        check queueReceiver->abandon(messageReceived);
        sb:Message|error? messageReceivedAgain = queueReceiver->receive(serverWaitTime);
        if (messageReceivedAgain is sb:Message) {
            check queueReceiver->complete(messageReceivedAgain);
            log:printInfo("Abandon message successful");
        } else {
            log:printError("Abandon message not succesful.");
        }
    } else if (messageReceived is ()) {
        log:printError("No message in the queue.");
    } else {
        log:printError("Receiving message via Asb receiver connection failed.");
    }

    log:printInfo("Closing Asb sender client.");
    check queueSender->close();

    log:printInfo("Closing Asb receiver client.");
    check queueReceiver->close();
}
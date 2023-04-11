/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KINDither express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.asb.receiver;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.models.AmqpAnnotatedMessage;
import com.azure.core.amqp.models.AmqpMessageBodyType;
import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder.ServiceBusReceiverClientBuilder;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.internal.types.BArrayType;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.ballerinax.asb.util.ASBConstants;
import org.ballerinax.asb.util.ASBUtils;
import org.ballerinax.asb.util.ModuleUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.ballerinax.asb.util.ASBConstants.RECEIVE_AND_DELETE;
import static org.ballerinax.asb.util.ASBUtils.getRetryOptions;

/**
 * This facilitates the client operations of MessageReceiver client in
 * Ballerina.
 */
public class MessageReceiver {

    private static final Logger log = Logger.getLogger(MessageReceiver.class);
    private ServiceBusReceiverClient receiver;

    /**
     * Parameterized constructor for Message Receiver (IMessageReceiver).
     *
     * @param connectionString         Azure service bus connection string.
     * @param queueName                QueueName
     * @param topicName                Topic Name
     * @param subscriptionName         Subscription Name
     * @param receiveMode              Receive Mode as PeekLock or Receive&Delete.
     * @param maxAutoLockRenewDuration Max lock renewal duration under Peek Lock mode.
     *                                 Setting to 0 disables auto-renewal.
     *                                 For RECEIVE_AND_DELETE mode, auto-renewal is disabled.
     * @throws ServiceBusException on failure initiating IMessage Receiver in Azure
     *                             Service Bus instance.
     */
    public MessageReceiver(String connectionString, String queueName, String topicName, String subscriptionName,
                           String receiveMode, long maxAutoLockRenewDuration, String logLevel,
                           BMap<BString, Object> retryConfigs) throws ServiceBusException {

        log.setLevel(Level.toLevel(logLevel, Level.OFF));
        AmqpRetryOptions retryOptions = getRetryOptions(retryConfigs);
        ServiceBusReceiverClientBuilder receiverClientBuilder = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .retryOptions(retryOptions)
                .receiver();
        if (!queueName.isEmpty()) {
            if (Objects.equals(receiveMode, RECEIVE_AND_DELETE)) {
                this.receiver = receiverClientBuilder
                        .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                        .queueName(queueName)
                        .buildClient();

            } else {
                this.receiver = receiverClientBuilder
                        .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                        .queueName(queueName)
                        .maxAutoLockRenewDuration(Duration.ofSeconds(maxAutoLockRenewDuration))
                        .buildClient();
            }
        } else if (!subscriptionName.isEmpty() && !topicName.isEmpty()) {
            if (Objects.equals(receiveMode, RECEIVE_AND_DELETE)) {
                this.receiver = receiverClientBuilder
                        .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                        .topicName(topicName)
                        .subscriptionName(subscriptionName)
                        .buildClient();
            } else {
                this.receiver = receiverClientBuilder
                        .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                        .topicName(topicName)
                        .subscriptionName(subscriptionName)
                        .maxAutoLockRenewDuration(Duration.ofSeconds(maxAutoLockRenewDuration))
                        .buildClient();
            }
        }
        log.debug("ServiceBusReceiverClient initialized");
    }

    /**
     * Receive Message with configurable parameters as Map when Receiver Connection
     * is given as a parameter and
     * server wait time in seconds to receive message and return Message object.
     *
     * @param endpointClient Ballerina ASB client object
     * @param serverWaitTime Specified server wait time in seconds to receive
     *                       message.
     * @return Message Object of the received message.
     */
    public Object receive(BObject endpointClient, Object serverWaitTime) {
        try {
            ServiceBusReceivedMessage receivedMessage = null;
            IterableStream<ServiceBusReceivedMessage> receivedMessages;
            Iterator<ServiceBusReceivedMessage> iterator;
            if (serverWaitTime != null) {
                receivedMessages = this.receiver.receiveMessages(1, Duration.ofSeconds((long) serverWaitTime));
            } else {
                receivedMessages = receiver.receiveMessages(1);
            }
            iterator = receivedMessages.iterator();
            while (iterator.hasNext()) {
                receivedMessage = iterator.next();
            }
            if (receivedMessage == null) {
                return null;
            }
            log.debug("Received message with messageId: " + receivedMessage.getMessageId());
            return getReceivedMessage(endpointClient, receivedMessage);
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Receive Batch of Messages with configurable parameters as Map when Receiver
     * Connection is given as a parameter,
     * maximum message count in a batch as int, server wait time in seconds and
     * return Batch Message object.
     *
     * @param endpointClient  Ballerina ASB client object
     * @param maxMessageCount Maximum no. of messages in a batch.
     * @param serverWaitTime  Server wait time.
     * @return Batch Message Object of the received batch of messages.
     */
    public Object receiveBatch(BObject endpointClient, Object maxMessageCount, Object serverWaitTime) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Waiting up to 'serverWaitTime' seconds for messages from " + receiver.getEntityPath());
            }
            return getReceivedMessageBatch(endpointClient, maxMessageCount, serverWaitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    private BMap<BString, Object> getReceivedMessageBatch(BObject endpointClient, Object maxMessageCount,
                                                          Object serverWaitTime)
            throws InterruptedException, ServiceBusException {

        int messageCount = 0;
        Map<String, Object> map = new HashMap<>();
        int maxCount = Long.valueOf(maxMessageCount.toString()).intValue();
        Object[] messages = new Object[maxCount];
        IterableStream<ServiceBusReceivedMessage> receivedMessageStream;
        if (serverWaitTime != null) {
            receivedMessageStream = receiver.receiveMessages(maxCount, Duration.ofSeconds((long) serverWaitTime));
        } else {
            receivedMessageStream = receiver.receiveMessages(maxCount);
        }
        BMap<BString, Object> messageRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.MESSAGE_RECORD);
        for (ServiceBusReceivedMessage receivedMessage : receivedMessageStream) {
            BMap<BString, Object> recordMap = getReceivedMessage(endpointClient, receivedMessage);
            messages[messageCount] = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.MESSAGE_RECORD, recordMap);
            messageCount = messageCount + 1;
        }
        BArrayType sourceArrayType = new BArrayType(TypeUtils.getType(messageRecord));
        map.put("messageCount", messageCount);
        map.put("messages", ValueCreator.createArrayValue(messages, sourceArrayType));
        return ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.MESSAGE_BATCH_RECORD, map);
    }

    /**
     * Completes Messages from Queue or Subscription based on messageLockToken.
     *
     * @param endpointClient Ballerina ASB client object
     * @param lockToken      Message lock token.
     * @return An error if failed to complete the message.
     */
    public Object complete(BObject endpointClient, BString lockToken) {
        try {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) endpointClient
                    .getNativeData(lockToken.getValue());
            receiver.complete(message);
            log.debug("Completed the message(Id: " + message.getMessageId() + ") with lockToken " + lockToken);
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Abandons message & make available again for processing from Queue or Subscription, based on messageLockToken.
     *
     * @param endpointClient Ballerina ASB client object
     * @param lockToken      Message lock token.
     * @return An error if failed to abandon the message.
     */
    public Object abandon(BObject endpointClient, BString lockToken) {
        try {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) endpointClient
                    .getNativeData(lockToken.getValue());
            receiver.abandon(message);
            log.debug("Done abandoning a message(Id: " + message.getMessageId() + ") using its lock token from \n"
                    + receiver.getEntityPath());
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Dead-Letter the message & moves the message to the Dead-Letter Queue based on messageLockToken.
     *
     * @param endpointClient             Ballerina ASB client object
     * @param lockToken                  Message lock token.
     * @param deadLetterReason           The dead letter reason.
     * @param deadLetterErrorDescription The dead letter error description.
     * @return An error if failed to dead letter the message.
     */
    public Object deadLetter(BObject endpointClient, BString lockToken, Object deadLetterReason,
                             Object deadLetterErrorDescription) {
        try {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) endpointClient
                    .getNativeData(lockToken.getValue());
            DeadLetterOptions options = new DeadLetterOptions()
                    .setDeadLetterErrorDescription(ASBUtils.convertString(deadLetterErrorDescription));
            options.setDeadLetterReason(ASBUtils.convertString(deadLetterReason));
            receiver.deadLetter(message, options);
            log.debug("Done dead-lettering a message(Id: " + message.getMessageId() + ") using its lock token from "
                    + receiver.getEntityPath());

            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Defer the message in a Queue or Subscription based on messageLockToken.
     *
     * @param endpointClient Ballerina ASB client object
     * @param lockToken      Message lock token.
     * @return An error if failed to defer the message.
     */
    public Object defer(BObject endpointClient, BString lockToken) {
        try {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) endpointClient
                    .getNativeData(lockToken.getValue());
            receiver.defer(message);
            log.debug("Done deferring a message(Id: " + message.getMessageId() + ") using its lock token from "
                    + receiver.getEntityPath());
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Receives a deferred Message. Deferred messages can only be received by using sequence number and return
     * Message object.
     *
     * @param endpointClient Ballerina ASB client object
     * @param sequenceNumber Unique number assigned to a message by Service Bus. The
     *                       sequence number is a unique 64-bit
     *                       integer assigned to a message as it is accepted and
     *                       stored by the broker and functions as
     *                       its true identifier.
     * @return The received Message or null if there is no message for given sequence number.
     */
    public Object receiveDeferred(BObject endpointClient, int sequenceNumber) {
        try {
            ServiceBusReceivedMessage receivedMessage = receiver.receiveDeferredMessage(sequenceNumber);
            if (receivedMessage == null) {
                return null;
            }
            log.debug("Received deferred message using its sequenceNumber from " + receiver.getEntityPath());
            return getReceivedMessage(endpointClient, receivedMessage);
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * The operation renews lock on a message in a queue or subscription based on
     * messageLockToken.
     *
     * @param endpointClient Ballerina ASB client object
     * @param lockToken      Message lock token.
     * @return An error if failed to renewLock of the message.
     */
    public Object renewLock(BObject endpointClient, BString lockToken) {
        try {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) endpointClient
                    .getNativeData(lockToken.getValue());
            receiver.renewMessageLock(message);
            log.debug("Done renewing a message(Id: " + message.getMessageId() + ") using its lock token from "
                    + receiver.getEntityPath());
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Closes the Asb Receiver Connection using the given connection parameters.
     *
     * @return An error if failed to close the receiver.
     */
    public Object closeReceiver() {
        try {
            receiver.close();
            log.debug("Closed the receiver");
            return null;
        } catch (Exception e) {
            return ASBUtils.returnErrorValue(e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Converts AMPQ Body value to Java objects.
     *
     * @param amqpValue AMQP Value type object
     */
    private Object convertAMQPToJava(String messageId, Object amqpValue) {
        log.debug("Type of amqpValue object  of received message " + messageId + " is " + amqpValue.getClass());
        Class<?> clazz = amqpValue.getClass();
        switch (clazz.getSimpleName()) {
            case "Integer":
            case "Long":
            case "Float":
            case "Double":
            case "String":
            case "Boolean":
            case "Byte":
            case "Short":
            case "Character":
            case "BigDecimal":
            case "Date":
            case "UUID":
                return amqpValue;
            default:
                log.debug("The type of amqpValue object " + clazz + " is not supported");
                return null;
        }
    }

    /**
     * Converts the received message to a Ballerina map.
     *
     * @param endpointClient Ballerina client object
     * @param message        Received Message
     */
    private BMap<BString, Object> getReceivedMessage(BObject endpointClient, ServiceBusReceivedMessage message) {
        Map<String, Object> map = new HashMap<>();
        Object body = getMessageContent(message);
        if (body instanceof byte[]) {
            byte[] bodyA = (byte[]) body;
            map.put("body", ValueCreator.createArrayValue(bodyA));
        } else {
            map.put("body", body);
        }
        if (message.getContentType() != null) {
            map.put("contentType", StringUtils.fromString(message.getContentType()));
        }
        map.put("messageId", StringUtils.fromString(message.getMessageId()));
        map.put("to", StringUtils.fromString(message.getTo()));
        map.put("replyTo", StringUtils.fromString(message.getReplyTo()));
        map.put("replyToSessionId", StringUtils.fromString(message.getReplyToSessionId()));
        map.put("label", StringUtils.fromString(message.getSubject()));
        map.put("sessionId", StringUtils.fromString(message.getSessionId()));
        map.put("correlationId", StringUtils.fromString(message.getCorrelationId()));
        map.put("partitionKey", StringUtils.fromString(message.getPartitionKey()));
        map.put("timeToLive", (int) message.getTimeToLive().getSeconds());
        map.put("sequenceNumber", (int) message.getSequenceNumber());
        map.put("lockToken", StringUtils.fromString(message.getLockToken()));
        map.put("deliveryCount", (int) message.getDeliveryCount());
        map.put("enqueuedTime", StringUtils.fromString(message.getEnqueuedTime().toString()));
        map.put("enqueuedSequenceNumber", (int) message.getEnqueuedSequenceNumber());
        map.put("deadLetterErrorDescription", StringUtils.fromString(message.getDeadLetterErrorDescription()));
        map.put("deadLetterReason", StringUtils.fromString(message.getDeadLetterReason()));
        map.put("deadLetterSource", StringUtils.fromString(message.getDeadLetterSource()));
        map.put("state", StringUtils.fromString(message.getState().toString()));
        map.put("applicationProperties", getApplicationProperties(message));
        BMap<BString, Object> createRecordValue = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.MESSAGE_RECORD, map);
        endpointClient.addNativeData(message.getLockToken(), message);
        return createRecordValue;
    }

    /**
     * Prepares the message body content.
     *
     * @param receivedMessage ASB received message
     */
    private Object getMessageContent(ServiceBusReceivedMessage receivedMessage) {
        AmqpAnnotatedMessage rawAmqpMessage = receivedMessage.getRawAmqpMessage();
        AmqpMessageBodyType bodyType = rawAmqpMessage.getBody().getBodyType();
        switch (bodyType) {
            case DATA:
                return rawAmqpMessage.getBody().getFirstData();
            case VALUE:
                Object amqpValue = rawAmqpMessage.getBody().getValue();
                log.debug("Received a message with messageId " + receivedMessage.getMessageId()
                        + " AMQPMessageBodyType:" + bodyType);

                amqpValue = convertAMQPToJava(receivedMessage.getMessageId(), amqpValue);
                return amqpValue;
            default:
                throw new RuntimeException("Invalid message body type: " + receivedMessage.getMessageId());
        }
    }

    private static BMap<BString, Object> getApplicationProperties(ServiceBusReceivedMessage message) {
        BMap<BString, Object> applicationPropertiesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.APPLICATION_PROPERTY_TYPE);
        MapType mapType = TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA);
        BMap<BString, Object> applicationProperties = ValueCreator.createMapValue(mapType);
        for (Map.Entry<String, Object> property : message.getApplicationProperties().entrySet()) {
            populateApplicationProperty(applicationProperties, property.getKey(), property.getValue());
        }
        return ValueCreator.createRecordValue(applicationPropertiesRecord, applicationProperties);
    }

    private static void populateApplicationProperty(BMap<BString, Object> applicationProperties,
                                                    String key, Object value) {
        BString propertyKey = StringUtils.fromString(key);
        if (value instanceof String) {
            applicationProperties.put(propertyKey, StringUtils.fromString((String) value));
        } else if (value instanceof Integer) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Long) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Float) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Double) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Boolean) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Character) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Byte) {
            applicationProperties.put(propertyKey, value);
        } else if (value instanceof Short) {
            applicationProperties.put(propertyKey, value);
        } else {
            applicationProperties.put(propertyKey, StringUtils.fromString(value.toString()));
        }
    }
}

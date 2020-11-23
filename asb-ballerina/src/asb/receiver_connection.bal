import ballerina/java;

public class ReceiverConnection{

    handle asbReceiverConnection;

    private string connectionString;
    private string entityPath;

    # Initiates an Asb Receiver Connection using the given connection configuration.
    # 
    # + connectionConfiguration - Configurations used to create a `asb:Connection`
    public isolated function init(ConnectionConfiguration connectionConfiguration) {
        self.connectionString = connectionConfiguration.connectionString;
        self.entityPath = connectionConfiguration.entityPath;
        self.asbReceiverConnection = <handle> createReceiverConnection(java:fromString(self.connectionString),java:fromString(self.entityPath));
    }

    # Creates a Asb Receiver Connection using the given connection parameters.
    # 
    # + connectionConfiguration - Configurations used to create a `asb:Connection`
    # + return - An `asb:Error` if failed to create connection or else `()`
    public isolated function createReceiverConnection(ConnectionConfiguration connectionConfiguration) returns handle|Error? {
        self.connectionString = connectionConfiguration.connectionString;
        self.entityPath = connectionConfiguration.entityPath;
        self.asbReceiverConnection = <handle> createReceiverConnection(java:fromString(self.connectionString),java:fromString(self.entityPath));
    }

    # Closes the Asb Receiver Connection using the given connection parameters.
    #
    # + return - An `asb:Error` if failed to close connection or else `()`
    public isolated function closeReceiverConnection() returns Error? {
        return closeReceiverConnection(self.asbReceiverConnection);
    }

    # Receive Message from queue.
    # 
    # + return - A Message object
    public isolated function receiveMessage() returns Message|Error {
        return receiveMessage(self.asbReceiverConnection);
    }

}

isolated function createReceiverConnection(handle connectionString, handle entityPath) returns handle|Error? = @java:Method {
    name: "createReceiverConnection",
    'class: "org.ballerinalang.asb.connection.ConUtils"
} external;

isolated function closeReceiverConnection(handle imessageSender) returns Error? = @java:Method {
    name: "closeReceiverConnection",
    'class: "org.ballerinalang.asb.connection.ConUtils"
} external;

isolated function receiveMessage(handle imessageReceiver) returns Message|Error = @java:Method {
    name: "receiveMessage",
    'class: "org.ballerinalang.asb.connection.ConUtils"
} external;
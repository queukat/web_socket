# WebSocketOrderedMessages
WebSocketOrderedMessages is a Scala application designed to process and forward messages from a raw WebSocket server to an ordered WebSocket server. Messages received from the raw server might be unordered, but this application ensures they are sent to the ordered server in a sequential manner based on their unique ID.

## Features
* **Message Reception:** Accepts messages from a raw WebSocket server.
* **Priority Queue:** Uses a priority queue to handle the ordering of incoming messages.
* **Sequential Forwarding:** Ensures messages forwarded to the ordered server are in the correct sequence.
* **Delay Measurement:** Records the minimum delay between receiving a message from the raw server and sending it to the ordered server.
## How It Works
1. **Initialization:** On startup, the application establishes connections to both the raw and ordered WebSocket servers.

2. **Message Handling:** Messages are received from the raw server and are either:
   * Directly sent to the ordered server if they are in sequence.
   * Or, added to a priority queue if they're out of order.

3. **Queue Management:** The priority queue holds messages that arrived out of order. The application continually checks the queue to see if any messages can be sent to the ordered server in the correct sequence.

4. **Efficiency:** The application employs a buffering strategy. If the next expected message hasn't been received yet, the application waits and buffers up incoming messages. This ensures that if the expected message is slightly delayed, it can be quickly processed without much wait, making the whole operation more efficient.

5. **Logging:** All significant events, such as connecting to servers, receiving messages, and sending messages, are logged for transparency and debugging purposes.

## Getting Started
### Prerequisites
Installed Scala
Installed SBT (Scala Build Tool)
### Installation
Clone the repository:

```bash 
git clone https://github.com/yourusername/WebSocketOrderedMessages.git
```

Navigate to the project directory:

```bash 
cd WebSocketOrderedMessages
```

Run the application using SBT:

```bash 
sbt run
```

## Usage
Once started, the application will automatically connect to the servers and initiate the process of receiving and forwarding messages.

## Logging
The application utilizes **'log4j'** for logging purposes. Logging configurations can be adjusted in the **'log4j.properties'** file.

## Contributing
If you'd like to contribute to this project, please fork the repository and submit a pull request.


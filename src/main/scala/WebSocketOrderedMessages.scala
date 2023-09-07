import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import play.api.libs.json._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable._
import org.apache.logging.log4j.{Logger, LogManager}

/**
 * WebSocketOrderedMessages Application
 *
 * This application acts as an intermediary between a raw server and an ordered server.
 * The raw server sends messages that might arrive out of order.
 * This application ensures that the messages are sent to the ordered server in the correct order based on their ID.
 */
object WebSocketOrderedMessages extends App {
  val logger: Logger = LogManager.getLogger(this.getClass)

  /**
   * URI for the raw messages server.
   */
  val rawMessagesUri = new URI(sys.env.getOrElse("RAW_MESSAGES_URI", "wss://default-url/raw-messages"))

  val candidateSurname = "surname"
  /**
   * URI for the ordered messages server.
   */
  val orderedMessagesUri = new URI(sys.env.getOrElse("ORDERED_MESSAGES_URI", s"wss://default-url/ordered-messages/$candidateSurname"))

  /**
   * Message case class to represent a message structure with an ID and text.
   *
   * @param id   The unique identifier of the message.
   * @param text The content of the message.
   */

  case class Message(id: Int, text: Int)

  /**
   * Implicit JSON format for the Message case class.
   */
  implicit val messageFormat = Json.format[Message]

  /**
   * Priority queue to hold messages that arrive out of order.
   * Messages with higher IDs are given higher priority to ensure ordered processing.
   */
  val queue = new PriorityQueue[Message]()(Ordering.by((_: Message).id).reverse)

  // Counters and metrics for monitoring performance and message statistics
  var messageCounter = 0
  var receivedMessagesCounter = 0
  var startTime = 0L
  var minDelay = Long.MaxValue

  /**
   * WebSocket client for the raw message server.
   * This client receives messages from the raw server, determines their order,
   * and forwards them in the correct order to the ordered server.
   */
  val rawClient = try {
    new WebSocketClient(rawMessagesUri) {

      /**
       * Closes the connection to the raw server.
       */
      def closeConnection(): Unit = {
        this.close()
      }

      override def onOpen(handshakedata: ServerHandshake): Unit = {
        logger.info("Connected to raw messages server")
        startTime = System.nanoTime() // Set the start time
      }

      override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
        logger.info("Disconnected from raw messages server")
      }

      override def onMessage(message: String): Unit = {
        // Check if the received message is a valid JSON
        if (!isValidJson(message)) {
          logger.error(s"Invalid JSON received: $message")
          return
        }
        receivedMessagesCounter += 1
        val count = receivedMessagesCounter
        if (count > 1000) {
          logger.info("Received 1000 messages. Closing connection.")
          closeConnection()
          return
        }
        val receivedTime = System.nanoTime()
        val msg = Json.parse(message).as[Message]
        if (lastSentId.isEmpty && initialBuffer.size < bufferSize) {
          initialBuffer += msg
          if (initialBuffer.size == bufferSize) {
            val minId = initialBuffer.map(_.id).min
            lastSentId = Some(minId - 1)
            initialBuffer.foreach(msg => queue.enqueue(msg))
            initialBuffer.clear()
          }
        } else {
          if (lastSentId.isEmpty) {
            lastSentId = Some(msg.id - 1)
          }
          if (msg.id == lastSentId.get + 1) {
            logger.info(s"Directly sending message with ID: ${msg.id}")
            sendMessage(msg)
            sendOrderedMessages()
          } else {
            logger.info(s"Enqueuing message with ID: ${msg.id}")
            queue.enqueue(msg)
            sendOrderedMessages()
          }
          val delay = System.nanoTime() - receivedTime
          if (delay < minDelay) {
            minDelay = delay
            logger.info(s"New minimum delay recorded: $delay nanoseconds")
          }
          logger.info(s"Received from raw server: $message,#${count}")
        }
      }

      override def onError(ex: Exception): Unit = {
        logger.info("An error occurred: " + ex.getMessage)
        ex.printStackTrace()
      }
    }
  } catch {
    case ex: Exception =>
      logger.error(s"Error initializing raw WebSocket client: ${ex.getMessage}", ex)
      null
  }


  /**
   * WebSocket client for the ordered message server.
   * This client sends messages to the ordered server in the correct order.
   */
  val orderedClient = try {
    new WebSocketClient(orderedMessagesUri) {

      override def onOpen(handshakedata: ServerHandshake): Unit = {
        logger.info("Connected to ordered messages server")
      }

      override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
        logger.info("Disconnected from ordered messages server")
      }

      override def onMessage(message: String): Unit = {
        // This method is part of the WebSocketClient interface and must be implemented.
        // However, within the context of our application, we do not anticipate or process incoming messages from the orderedClient.
        // For this reason, the method's implementation is left empty. If this method is removed or left unimplemented,
        // the application will throw a compilation error due to the missing implementation of a mandatory abstract method.
      }

      override def onError(ex: Exception): Unit = {
        ex.printStackTrace()
      }
    }
  } catch {
    case ex: Exception =>
      logger.error(s"Error initializing ordered WebSocket client: ${ex.getMessage}", ex)
      null
  }

  // Start the WebSocket clients
  if (rawClient != null) {
    rawClient.connect()
  } else {
    logger.error("Raw client is not initialized. Exiting the application.")
  }

  if (orderedClient != null) {
    orderedClient.connect()
  } else {
    logger.error("Ordered client is not initialized. Exiting the application.")
  }

  rawClient.connect()
  orderedClient.connect()

  // Helper variables for message ordering
  var lastSentId: Option[Int] = None
  val initialBuffer: mutable.ListBuffer[Message] = mutable.ListBuffer()
  val bufferSize = 10 // Chosen buffer size. Adjust as per application needs.

  /**
   * Attempts to send ordered messages from the queue.
   * If the next expected message ID matches the ID of the message at the head of the queue,
   * that message is dequeued and sent to the ordered server.
   */
  def sendOrderedMessages(): Unit = {
    logger.info("sendOrderedMessages called!")
    while (queue.nonEmpty && queue.head.id == lastSentId.get + 1) {
      val msg = queue.dequeue()
      logger.info(s"Sending message with ID: ${msg.id} from the queue")
      sendMessage(msg)
    }
    logger.info("sendOrderedMessages finished!")
  }

  /**
   * Sends a message to the ordered server.
   *
   * @param msg The message to be sent.
   */
  def sendMessage(msg: Message): Unit = {
    try {
      logger.info(s"Attempting to send message with ID: ${msg.id}")

      orderedClient.send(Json.toJson(msg).toString())
      logger.info(s"Message with ID: ${msg.id} sent successfully")
      lastSentId = Some(msg.id)
      messageCounter += 1
      if (messageCounter >= 1000) {
        // Close connections after processing 1000 messages
        rawClient.close()
        orderedClient.close()
        val totalTime = System.nanoTime() - startTime
        logger.info(s"Total time taken: $totalTime nanoseconds")
        logger.info(s"Minimum delay: $minDelay nanoseconds")
      }
      logger.info(s"Sending ordered message: ${Json.toJson(msg).toString()}")
    } catch {
      case ex: Exception =>
        logger.error(s"Error sending message with ID: ${msg.id}. Error: ${ex.getMessage}", ex)
    }
  }

  /**
   * Checks if a given message string is a valid JSON representation of a Message.
   *
   * @param message The message string to be checked.
   * @return True if valid, False otherwise.
   */
  def isValidJson(message: String): Boolean = {
    try {
      val json = Json.parse(message)
      (json \ "id").asOpt[Int].isDefined && (json \ "text").asOpt[Int].isDefined
    } catch {
      case _: Exception => false
    }
  }
}

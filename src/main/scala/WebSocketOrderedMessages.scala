import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import play.api.libs.json._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable._
import org.apache.logging.log4j.{Logger,LogManager}

/**
 * WebSocketOrderedMessages is an application to process and forward messages from a raw server to an ordered server.
 * Messages from the raw server may arrive out of order.
 * The application ensures that messages sent to the ordered server are in the correct order based on their ID.
 */
object WebSocketOrderedMessages extends App {
  val logger: Logger = LogManager.getLogger(this.getClass)

  /**
   * URI for the raw messages server.
   */
  val rawMessagesUri = new URI("wss://test-ws.skns.dev/raw-messages")
  val candidateSurname = "surname"
  /**
   * URI for the ordered messages server.
   */
  val orderedMessagesUri = new URI(s"wss://test-ws.skns.dev/ordered-messages/$candidateSurname")

  /**
   * Represents a message with an ID and text.
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
   * Priority queue to hold messages that are out of order.
   * Messages with higher IDs have higher priority.
   */
  val queue = new PriorityQueue[Message]()(Ordering.by((_:Message).id).reverse)

  // Counters and time measurements
  val messageCounter = new AtomicInteger(0)
  val receivedMessagesCounter = new AtomicInteger(0)
  val startTime = new AtomicLong(0)
  val minDelay = new AtomicLong(Long.MaxValue)

  /**
   * The WebSocket client for the raw message server.
   * This client receives messages, determines their order, and forwards them to the ordered server.
   */
  val rawClient = new WebSocketClient(rawMessagesUri) {

    /**
     * Closes the connection to the raw server.
     */
    def closeConnection(): Unit = {
      this.close()
    }

    override def onOpen(handshakedata: ServerHandshake): Unit = {
      logger.info("Connected to raw messages server")
      startTime.set(System.nanoTime()) // Set the start time
    }

    override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
      logger.info("Disconnected from raw messages server")
    }

    override def onMessage(message: String): Unit = {
      val count = receivedMessagesCounter.incrementAndGet()
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
          sendOrderedMessages() // проверьте, можно ли отправить другие сообщения из очереди
        } else {
          logger.info(s"Enqueuing message with ID: ${msg.id}")
          queue.enqueue(msg)
          sendOrderedMessages()
        }
        val delay = System.nanoTime() - receivedTime
        if (delay < minDelay.get()) {
          minDelay.set(delay)
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

  /**
   * The WebSocket client for the ordered message server.
   * This client sends messages in the correct order.
   */
  val orderedClient = new WebSocketClient(orderedMessagesUri) {
    override def onOpen(handshakedata: ServerHandshake): Unit = {
      logger.info("Connected to ordered messages server")
    }

    override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
      logger.info("Disconnected from ordered messages server")
    }

    override def onMessage(message: String): Unit = {
      // Пока что этот метод может быть пустым, если вы не планируете что-то делать с сообщениями от ordered server
    }

    override def onError(ex: Exception): Unit = {
      ex.printStackTrace()
    }
  }

  rawClient.connect()
  orderedClient.connect()

  var lastSentId: Option[Int] = None
  val initialBuffer: mutable.ListBuffer[Message] = mutable.ListBuffer()
  val bufferSize = 10  // Выберите размер буфера, который вы считаете подходящим

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
      logger.info(s"Attempting to send message with ID: ${msg.id}")
      orderedClient.send(Json.toJson(msg).toString())
      logger.info(s"Message with ID: ${msg.id} sent successfully")
      lastSentId = Some(msg.id)
      val count = messageCounter.incrementAndGet()
      if (count >= 1000) {
        // Close connections after processing 1000 messages
        rawClient.close()
        orderedClient.close()
        val totalTime = System.nanoTime() - startTime.get()
        logger.info(s"Total time taken: $totalTime nanoseconds")
        logger.info(s"Minimum delay: ${minDelay.get()} nanoseconds")
      }
      logger.info(s"Sending ordered message: ${Json.toJson(msg).toString()}")
    }
}

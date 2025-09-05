package github.umer0586.sensorserver.mqttclient

import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedMessage(
    val topic: String,
    val message: String, 
    val timestamp: Long = System.currentTimeMillis()
)

class MessageQueue(private val maxAge: Long = 10_000) {
    private val queue = ConcurrentLinkedQueue<QueuedMessage>()
    
    fun add(topic: String, message: String) {
        cleanOldMessages()
        queue.offer(QueuedMessage(topic, message))
    }
    
    fun drainTo(publisher: (String, String) -> Boolean) {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val msg = iterator.next()
            if (isExpired(msg)) {
                iterator.remove()
                continue
            }
            
            if (publisher(msg.topic, msg.message)) {
                iterator.remove()
            }
        }
    }
    
    private fun cleanOldMessages() {
        // Use iterator to remove old messages
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (isExpired(iterator.next())) {
                iterator.remove()
            }
        }
    }
    
    private fun isExpired(message: QueuedMessage): Boolean {
        return System.currentTimeMillis() - message.timestamp > maxAge
    }
    
    fun size(): Int = queue.size
}
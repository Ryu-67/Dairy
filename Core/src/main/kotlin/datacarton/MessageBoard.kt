package datacarton

import collections.LimitedQueue
import datacarton.CartonComponent.TraceComponentBuilder

@Suppress("unused")
open class MessageBoard(protected val dataLineQueue: LimitedQueue<DataLine>) : CartonComponent {
	private var cachedBuild = ""
	protected open fun rebuild(): String {
		val builder = StringBuilder()
		for (line in dataLineQueue) {
			builder.append(line).append("\n")
		}
		return builder.toString()
	}

	override fun toString(): String {
		return cachedBuild
	}

	override fun getData(): Collection<DataLine?> {
		return dataLineQueue
	}

	override fun add(dataLine: DataLine?) {
		dataLineQueue.offer(dataLine)
		cachedBuild = rebuild()
	}

	class Builder : TraceComponentBuilder {
		private var length = 0

		override fun add(dataLine: DataLine?) {
			length++
		}

		override fun build(settings: Any?): CartonComponent? {
			val castSettings = settings as Settings?
			if (length == 0) return null
			if (castSettings?.len != null) {
				length = castSettings.len!!
			} else if (castSettings?.minLen != null) {
				length = length.coerceAtLeast(castSettings.minLen!!)
			}
			length.coerceAtLeast(3)
			return if (castSettings != null && castSettings.reversed) Reversed(LimitedQueue(length))
			else MessageBoard(LimitedQueue(length))
		}
	}

	class Settings constructor(var reversed: Boolean = true, var len: Int? = null, var minLen: Int? = null) {
		constructor() : this(true, null, 3)
	}

	class Reversed(dataLineQueue: LimitedQueue<DataLine>) : MessageBoard(dataLineQueue) {
		override fun rebuild(): String {
			val builder = StringBuilder()
			val it: Iterator<DataLine?> = dataLineQueue.reverseIterator()
			while (it.hasNext()) {
				val line = it.next()
				builder.append(line).append("\n")
			}
			return builder.toString()
		}
	}
}

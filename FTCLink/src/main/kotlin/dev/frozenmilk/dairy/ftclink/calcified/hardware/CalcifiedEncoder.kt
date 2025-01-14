package dev.frozenmilk.dairy.ftclink.calcified.hardware

import com.qualcomm.hardware.lynx.commands.core.LynxResetMotorEncoderCommand
import dev.frozenmilk.dairy.ftclink.calcified.CalcifiedModule
import dev.frozenmilk.dairy.ftclink.calcified.hardware.controller.CachedCompoundSupplier
import dev.frozenmilk.dairy.ftclink.calcified.hardware.controller.CompoundSupplier
import dev.frozenmilk.dairy.ftclink.geometry.angle.AngleDegrees
import dev.frozenmilk.dairy.ftclink.geometry.angle.AngleRadians

abstract class CalcifiedEncoder<T> internal constructor(internal val module: CalcifiedModule, internal val port: Byte) {
	var direction = Direction.FORWARD
	protected abstract val _positionSupplier: CachedCompoundSupplier<T, Double>
	protected abstract val _velocitySupplier: CachedCompoundSupplier<Double, Double>
	val positionSupplier: CompoundSupplier<T, Double>
		get() = _positionSupplier

	val velocitySupplier: CompoundSupplier<Double, Double>
		get() = _velocitySupplier

	/**
	 * ensures that the velocity cache is cleared first, so it can store the previous position
	 */
	internal open fun clearCache() {
		_velocitySupplier.clearCache()
		_positionSupplier.clearCache()
	}

	fun getPosition(): T {
		return _positionSupplier.get()
	}

	fun getVelocity(): Double {
		return _velocitySupplier.get()
	}

	fun reset() {
		LynxResetMotorEncoderCommand(module.lynxModule, port.toInt()).send()
	}
}

class TicksEncoder internal constructor(module: CalcifiedModule, port: Byte) : CalcifiedEncoder<Int>(module, port) {
	override val _positionSupplier: CachedCompoundSupplier<Int, Double> = object : CachedCompoundSupplier<Int, Double> {
		private var cachedPosition: Int? = null
		private var cachedError: Double? = null

		/**
		 * returns error in ticks, consider wrapping this encoder in a different UnitEncoder to use error with some other, more predictable unit
		 */
		override fun getError(target: Int): Double {
			if (cachedError == null) cachedError = (target - get()).toDouble()
			return cachedError!!
		}

		override fun clearCache() {
			cachedError = null
			cachedPosition = null
		}

		override fun get(): Int {
			if (cachedPosition == null) cachedPosition = module.bulkData.getEncoder(port.toInt()) * direction.multiplier
			return cachedPosition!!
		}
	}

	override val _velocitySupplier: CachedCompoundSupplier<Double, Double> = object : CachedCompoundSupplier<Double, Double> {
		private var cachedVelocity: Double? = null
		private var cachedError: Double? = null
		private var previousPosition = _positionSupplier.get()

		override fun getError(target: Double): Double {
			if (cachedError == null) cachedError = target - get()
			return cachedError!!
		}

		override fun clearCache() {
			previousPosition = _positionSupplier.get()
			cachedError = null
			cachedVelocity = null
		}

		/**
		 * the velocity since the last time this method was called
		 */
		override fun get(): Double {
			if (cachedVelocity == null) {
				cachedVelocity = (_positionSupplier.get() - previousPosition).toDouble() / (module.cachedTime - module.previousCachedTime)
			}
			return cachedVelocity!!
		}
	}
}

abstract class UnitEncoder<T>(private val ticksEncoder: TicksEncoder, protected val ticksPerUnit: Double) : CalcifiedEncoder<T>(ticksEncoder.module, ticksEncoder.port) {
	/**
	 * clears this cache first, then the ticks encoder cache, to ensure that we can grab previous values from the TicksEncoderCache
	 */
	override fun clearCache() {
		super.clearCache()
		ticksEncoder.clearCache()
	}
}

class RadiansEncoder internal constructor(ticksEncoder: TicksEncoder, ticksPerRevolution: Double) : UnitEncoder<AngleRadians>(ticksEncoder, ticksPerRevolution) {
	override val _positionSupplier: CachedCompoundSupplier<AngleRadians, Double> = object : CachedCompoundSupplier<AngleRadians, Double> {
		private var cachedAngle: AngleRadians? = null
		private var cachedError: Double? = null

		override fun getError(target: AngleRadians): Double {
			if (cachedError == null) cachedError = get().findShortestDistance(target)
			return cachedError!!
		}

		override fun clearCache() {
			cachedAngle = null
			cachedError = null
		}

		override fun get(): AngleRadians {
			if (cachedAngle == null) cachedAngle = AngleRadians(Math.PI * 2 * (ticksEncoder.positionSupplier.get() / ticksPerRevolution) * direction.multiplier)
			return cachedAngle!!
		}
	}

	override val _velocitySupplier: CachedCompoundSupplier<Double, Double> = object : CachedCompoundSupplier<Double, Double> {
		private var cachedVelocity: Double? = null
		private var cachedError: Double? = null
		private var previousPosition = _positionSupplier.get()

		override fun getError(target: Double): Double {
			if (cachedError == null) cachedError = target - get()
			return cachedError!!
		}

		override fun clearCache() {
			previousPosition = _positionSupplier.get()
			cachedVelocity = null
			cachedError = null
		}

		override fun get(): Double {
			if (cachedVelocity == null) cachedVelocity = (_positionSupplier.get() - previousPosition).theta / (module.cachedTime - module.previousCachedTime)
			return cachedVelocity!!
		}
	}
}

class DegreesEncoder internal constructor(ticksEncoder: TicksEncoder, ticksPerRevolution: Double) : UnitEncoder<AngleDegrees>(ticksEncoder, ticksPerRevolution) {
	override val _positionSupplier: CachedCompoundSupplier<AngleDegrees, Double> = object : CachedCompoundSupplier<AngleDegrees, Double> {
		private var cachedAngle: AngleDegrees? = null
		private var cachedError: Double? = null

		override fun getError(target: AngleDegrees): Double {
			if (cachedError == null) cachedError = get().findShortestDistance(target)
			return cachedError!!
		}

		override fun clearCache() {
			cachedAngle = null
			cachedError = null
		}

		override fun get(): AngleDegrees {
			if (cachedAngle == null) cachedAngle = AngleDegrees(360 * (ticksEncoder.positionSupplier.get() / ticksPerRevolution) * direction.multiplier)
			return cachedAngle!!
		}
	}

	override val _velocitySupplier: CachedCompoundSupplier<Double, Double> = object : CachedCompoundSupplier<Double, Double> {
		private var cachedVelocity: Double? = null
		private var cachedError: Double? = null
		private var previousPosition = _positionSupplier.get()

		override fun getError(target: Double): Double {
			if (cachedError == null) cachedError = target - get()
			return cachedError!!
		}

		override fun clearCache() {
			previousPosition = _positionSupplier.get()
			cachedVelocity = null
			cachedError = null
		}

		override fun get(): Double {
			if (cachedVelocity == null) cachedVelocity = (_positionSupplier.get() - previousPosition).theta / (module.cachedTime - module.previousCachedTime)
			return cachedVelocity!!
		}
	}
}
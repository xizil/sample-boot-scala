package sample.context.lock

import java.util.concurrent.locks.ReentrantReadWriteLock
import com.fasterxml.jackson.annotation.JsonValue
import sample.InvocationException
import sample.context._
import sample.ErrorKeys

/**
 * ID単位のロックを表現します。
 * low: ここではシンプルに口座単位のIDロックのみをターゲットにします。
 * low: 通常はDBのロックテーブルに"for update"要求で悲観的ロックをとったりしますが、サンプルなのでメモリロックにしてます。
 */
class IdLockHandler {
  val lockMap: scala.collection.mutable.Map[String, ReentrantReadWriteLock] =
    scala.collection.mutable.Map()

  /** IDロック上で処理を実行します。 */
  def call[T](id: String, lockType: LockType, callable: => T): T = {
    if (lockType.isWrite) {
      writeLock(id)
    } else {
      readLock(id)
    }
    try {
      callable
    } catch {
      case e: RuntimeException => throw e
      case e: Exception => throw InvocationException(ErrorKeys.Exception, e);
    } finally {
      unlock(id);
    }
  }
    
  def writeLock(id: String): Unit =
    Option(id).map(v =>
      lockMap.synchronized(idLock(v).writeLock().lock()))

  private def idLock(id: String): ReentrantReadWriteLock =
    lockMap.getOrElseUpdate(id, new ReentrantReadWriteLock())

  def readLock(id: String): Unit =
    Option(id).map(v =>
      lockMap.synchronized(idLock(v).readLock().lock()))

  def unlock(id: String): Unit =
    Option(id).map(v =>
      lockMap.synchronized(
        idLock(v) match {
          case lock if lock.isWriteLockedByCurrentThread() => lock.writeLock().unlock()
          case lock => lock.readLock().unlock()
        }
      ))
}

/**
 * ロック種別を表現するEnum。
 */
sealed trait LockType extends EnumSealed {
  @JsonValue def value: String = this.toString()
  def isRead: Boolean = !isWrite
  def isWrite: Boolean = this == LockType.WRITE
}
object LockType extends Enums[LockType] {
  /** 読み取り専用ロック */
  case object READ extends LockType
  /** 読み書き専用ロック */
  case object WRITE extends LockType
  
  override def values = List(READ, WRITE)
}
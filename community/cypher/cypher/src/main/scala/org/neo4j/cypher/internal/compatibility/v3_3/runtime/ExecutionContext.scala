/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compatibility.v3_3.runtime

import org.neo4j.cypher.internal.compatibility.v3_3.runtime.pipes.MutableMaps
import org.neo4j.cypher.internal.frontend.v3_3.InternalException

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.{Iterator, immutable}

object ExecutionContext {
  def empty: ExecutionContext = apply()

  def from(x: (String, Any)*): ExecutionContext = apply().newWith(x)

  def apply(m: MutableMap[String, Any] = MutableMaps.empty) = new ExecutionContext(m, 0)

  def apply(numberOfLongs: Int) = new ExecutionContext(m = null, numberOfLongs = numberOfLongs)
}

case class ExecutionContext private (m: MutableMap[String, Any], numberOfLongs: Int)
  extends MutableMap[String, Any] {

  def copyFrom(input: ExecutionContext): Unit = {
    if (input.numberOfLongs > numberOfLongs)
      throw new InternalException("Tried to copy more data into less.")

    System.arraycopy(input.longs, 0, longs, 0, input.numberOfLongs)
  }

  private val longs = new Array[Long](numberOfLongs)

  def setLongAt(offset: Int, value: Long): Unit = longs(offset) = value
  def getLongAt(offset: Int): Long = longs(offset)

  def get(key: String): Option[Any] = m.get(key)

  def iterator: Iterator[(String, Any)] = m.iterator

  override def size = m.size

  def ++(other: ExecutionContext): ExecutionContext = copy(m = m ++ other.m)

  override def foreach[U](f: ((String, Any)) => U) {
    m.foreach(f)
  }

  def +=(kv: (String, Any)) = {
    m += kv
    this
  }

  def -=(key: String) = {
    m -= key
    this
  }

  override def toMap[T, U](implicit ev: (String, Any) <:< (T, U)): immutable.Map[T, U] = m.toMap(ev)

  def newWith(newEntries: Seq[(String, Any)]) =
    createWithNewMap(m.clone() ++= newEntries)

  def newWith(newEntries: scala.collection.Map[String, Any]) =
    createWithNewMap(m.clone() ++= newEntries)

  def newFrom(newEntries: Seq[(String, Any)]) =
    createWithNewMap(MutableMaps.create(newEntries: _*))

  def newFromMutableMap(newEntries: scala.collection.mutable.Map[String, Any]) =
    createWithNewMap(newEntries)

  def newWith(newEntry: (String, Any)) =
    createWithNewMap(m.clone() += newEntry)

  // This may seem silly but it has measurable impact in tight loops

  def newWith1(key1: String, value1: Any) = {
    val newMap = m.clone()
    newMap.put(key1, value1)
    createWithNewMap(newMap)
  }

  def newWith2(key1: String, value1: Any, key2: String, value2: Any) = {
    val newMap = m.clone()
    newMap.put(key1, value1)
    newMap.put(key2, value2)
    createWithNewMap(newMap)
  }

  def newWith3(key1: String, value1: Any, key2: String, value2: Any, key3: String, value3: Any) = {
    val newMap = m.clone()
    newMap.put(key1, value1)
    newMap.put(key2, value2)
    newMap.put(key3, value3)
    createWithNewMap(newMap)
  }

  override def clone(): ExecutionContext = createWithNewMap(m.clone())

  protected def createWithNewMap(newMap: MutableMap[String, Any]) = {
    copy(m = newMap)
  }
}

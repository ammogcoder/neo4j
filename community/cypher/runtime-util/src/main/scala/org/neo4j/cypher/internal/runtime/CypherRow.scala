/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.cypher.internal.runtime

import org.neo4j.cypher.internal.expressions.ASTCachedProperty
import org.neo4j.exceptions.InternalException
import org.neo4j.graphdb.NotFoundException
import org.neo4j.memory.Measurable
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Value
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualRelationshipValue

import scala.collection.mutable

object CypherRow {
  def empty: CypherRow = apply()

  def from(x: (String, AnyValue)*): CypherRow = {
    val context = empty
    context.set(x)
    context
  }

  def apply(m: mutable.Map[String, AnyValue] = MutableMaps.empty): MapCypherRow = new MapCypherRow(m, null)
}

case class ResourceLinenumber(filename: String, linenumber: Long, last: Boolean = false)

trait CypherRow extends ReadWriteRow with Measurable {

  @deprecated
  def containsName(name: String): Boolean
  @deprecated
  def numberOfColumns: Int

  def createClone(): CypherRow

  def copyWith(key: String, value: AnyValue): CypherRow
  def copyWith(key1: String, value1: AnyValue, key2: String, value2: AnyValue): CypherRow
  def copyWith(key1: String, value1: AnyValue, key2: String, value2: AnyValue, key3: String, value3: AnyValue): CypherRow
  def copyWith(newEntries: Seq[(String, AnyValue)]): CypherRow


  def isNull(key: String): Boolean

  private var linenumber: Option[ResourceLinenumber] = None

  override def setLinenumber(file: String, line: Long, last: Boolean = false): Unit = {
    // sets the linenumber for the first time, overwrite since it would mean we have a LoadCsv in a LoadCsv
    linenumber = Some(ResourceLinenumber(file, line, last))
  }

  override def setLinenumber(line: Option[ResourceLinenumber]): Unit = linenumber match {
    // used to copy the linenumber when copying the ExecutionContext, don't want to overwrite it
    case None => linenumber = line
    case _ =>
  }

  override def getLinenumber: Option[ResourceLinenumber] = linenumber
}

class MapCypherRow(private val m: mutable.Map[String, AnyValue], private var cachedProperties: mutable.Map[ASTCachedProperty, Value] = null)
  extends CypherRow {

  override def copyTo(target: WritableRow, sourceLongOffset: Int = 0, sourceRefOffset: Int = 0, targetLongOffset: Int = 0, targetRefOffset: Int = 0): Unit = fail()

  override def copyFrom(input: ReadableRow, nLongs: Int, nRefs: Int): Unit = fail()

  def remove(name: String): Option[AnyValue] = m.remove(name)
  //used for testing
  def toMap: Map[String, AnyValue] = m.toMap

  override def getByName(name: String): AnyValue = m.getOrElse(name, throw new NotFoundException(s"Unknown variable `$name`."))
  override def containsName(name: String): Boolean = m.contains(name)
  override def numberOfColumns: Int = m.size

  override def setLongAt(offset: Int, value: Long): Unit = fail()
  override def getLongAt(offset: Int): Long = fail()

  override def setRefAt(offset: Int, value: AnyValue): Unit = fail()
  override def getRefAt(offset: Int): AnyValue = fail()

  private def fail(): Nothing = throw new InternalException("Tried using a map context as a slotted context")

  override def mergeWith(other: ReadableRow, entityById: EntityById): Unit = other match {
    case otherMapCtx: MapCypherRow =>
      m ++= otherMapCtx.m
      if (otherMapCtx.cachedProperties != null) {
        if (cachedProperties == null) {
          cachedProperties = otherMapCtx.cachedProperties.clone()
        } else {
          cachedProperties ++= otherMapCtx.cachedProperties
        }
      } else {
        //otherMapCtx.cachedProperties is null so do nothing
      }
      setLinenumber(otherMapCtx.getLinenumber)
    case _ => fail()
  }

  override def set(newEntries: Seq[(String, AnyValue)]): Unit =
    m ++= newEntries

  // This may seem silly but it has measurable impact in tight loops

  override def set(key: String, value: AnyValue): Unit =
    m.put(key, value)

  override def set(key1: String, value1: AnyValue, key2: String, value2: AnyValue): Unit = {
    m.put(key1, value1)
    m.put(key2, value2)
  }

  override def set(key1: String, value1: AnyValue, key2: String, value2: AnyValue, key3: String, value3: AnyValue): Unit = {
    m.put(key1, value1)
    m.put(key2, value2)
    m.put(key3, value3)
  }

  override def copyWith(key: String, value: AnyValue): CypherRow = {
    val newMap = m.clone()
    newMap.put(key, value)
    cloneFromMap(newMap)
  }

  override def copyWith(key1: String, value1: AnyValue, key2: String, value2: AnyValue): CypherRow = {
    val newMap = m.clone()
    newMap.put(key1, value1)
    newMap.put(key2, value2)
    cloneFromMap(newMap)
  }

  override def copyWith(key1: String, value1: AnyValue,
                        key2: String, value2: AnyValue,
                        key3: String, value3: AnyValue): CypherRow = {
    val newMap = m.clone()
    newMap.put(key1, value1)
    newMap.put(key2, value2)
    newMap.put(key3, value3)
    cloneFromMap(newMap)
  }

  override def copyWith(newEntries: Seq[(String, AnyValue)]): CypherRow = {
    cloneFromMap(m.clone() ++ newEntries)
  }

  override def createClone(): CypherRow = cloneFromMap(m.clone())

  override def isNull(key: String): Boolean =
    m.get(key) match {
      case Some(v) if v eq Values.NO_VALUE => true
      case _ => false
    }

  override def setCachedProperty(key: ASTCachedProperty, value: Value): Unit = {
    if (cachedProperties == null) {
      cachedProperties = mutable.Map.empty
    }
    cachedProperties.put(key, value)
  }

  override def setCachedPropertyAt(offset: Int, value: Value): Unit = fail()

  override def getCachedProperty(key: ASTCachedProperty): Value = {
    if (cachedProperties == null) {
      null
    } else {
      cachedProperties.getOrElse(key, null)
    }
  }

  override def getCachedPropertyAt(offset: Int): Value = fail()

  override def invalidateCachedNodeProperties(node: Long): Unit = {
    if (cachedProperties != null) {
      cachedProperties.keys.filter(cnp => getByName(cnp.entityName) match {
        case n: VirtualNodeValue => n.id() == node
        case _ => false
      }).foreach(cnp => setCachedProperty(cnp, null))
    }
  }

  override def invalidateCachedRelationshipProperties(rel: Long): Unit = {
    if (cachedProperties != null) {
      cachedProperties.keys.filter(cnp => getByName(cnp.entityName) match {
        case r: VirtualRelationshipValue => r.id() == rel
        case _ => false
      }).foreach(cnp => setCachedProperty(cnp, null))
    }
  }

  override def estimatedHeapUsage: Long = {
    var total = 0L
    val iterator = m.valuesIterator
    while (iterator.hasNext) {
      total += iterator.next().estimatedHeapUsage()
    }
    if (cachedProperties != null) {
      val iterator = cachedProperties.valuesIterator
      while (iterator.hasNext) {
        total += iterator.next().estimatedHeapUsage()
      }
    }
    total
  }

  private def cloneFromMap(newMap: mutable.Map[String, AnyValue]): CypherRow = {
    val newCachedProperties = if (cachedProperties == null) null else cachedProperties.clone()
    val map = new MapCypherRow(newMap, newCachedProperties)
    map.setLinenumber(getLinenumber)
    map
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[MapCypherRow]

  override def equals(other: Any): Boolean = other match {
    case that: MapCypherRow =>
      (that canEqual this) &&
        m == that.m
    case _ => false
  }

  override def hashCode(): Int = m.hashCode()

  override def toString: String = s"MapExecutionContext(m=$m, cached=$cachedProperties)"
}
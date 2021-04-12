/* This file is part of streamevmon.
 *
 * Copyright (C) 2021  The University of Waikato, Hamilton, New Zealand
 *
 * Author: Daniel Oosterwijk
 *
 * All rights reserved.
 *
 * This code has been developed by the University of Waikato WAND
 * research group. For further information please see https://wand.nz,
 * or our Github organisation at https://github.com/wanduow
 *
 * This program is free software: you can redistribute it and/or modify
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

package nz.net.wand.streamevmon.events.grouping.graph.building

import nz.net.wand.streamevmon.events.grouping.graph.GraphType._
import nz.net.wand.streamevmon.events.grouping.graph.NoReflectionUnusableEdgeSupplier
import nz.net.wand.streamevmon.events.grouping.graph.building.GraphChangeEvent._
import nz.net.wand.streamevmon.test.TestBase

import java.time.Instant

import scala.collection.JavaConverters._

class GraphChangeEventTest extends TestBase {
  "GraphChangeEvent subtypes" should {
    "behave correctly" when {

      def emptyGraph = {
        val graph = new GraphT(classOf[EdgeT])
        graph.setEdgeSupplier(new NoReflectionUnusableEdgeSupplier[EdgeT])
        graph
      }

      val v1 = new VertexT(
        Set("abc.example.org"),
        Set(),
        Set(),
        None
      )
      val v2 = new VertexT(
        Set("xyz.example.org"),
        Set(),
        Set(),
        None
      )
      val v3 = new VertexT(
        Set("qrs.example.org"),
        Set(),
        Set(),
        None
      )

      def nonEmptyGraph = {
        val graph = new GraphT(classOf[EdgeT])
        graph.setEdgeSupplier(new NoReflectionUnusableEdgeSupplier[EdgeT])
        graph.addVertex(v1)
        graph.addVertex(v2)
        graph.addEdge(v1, v2, new EdgeT(Instant.EPOCH))
        graph
      }

      def edgelessGraph = {
        val g = nonEmptyGraph
        g.removeEdge(v1, v2)
        g
      }

      "type is AddVertex" in {
        val g = emptyGraph
        AddVertex(v1).apply(g)
        g.vertexSet should have size 1
        g.vertexSet should contain(v1)
      }

      "type is RemoveVertex" in {
        val g = nonEmptyGraph
        RemoveVertex(v1).apply(g)
        g.vertexSet should have size 1
        g.vertexSet should contain(v2)
        g.edgeSet should have size 0
      }

      "type is UpdateVertex" when {
        "vertices are the same" in {
          UpdateVertex.create(v2, v2) shouldBe DoNothing()
          UpdateVertex(v2, v2) shouldBe Left(DoNothing())
        }

        "vertices are different" in {
          val g = nonEmptyGraph
          UpdateVertex(v2, v3) shouldBe a[Right[_, UpdateVertex]]
          UpdateVertex.create(v2, v3) shouldBe an[UpdateVertex]
          UpdateVertex.create(v2, v3).apply(g)
          g.vertexSet should contain(v3)
          g.incomingEdgesOf(v3) should have size 1
        }
      }

      "type is AddEdge" in {
        val g = edgelessGraph
        AddEdge(v1, v2, new EdgeT(Instant.EPOCH)).apply(g)
        g.outgoingEdgesOf(v1) should have size 1
        g.incomingEdgesOf(v2) should have size 1
        g.vertexSet shouldBe edgelessGraph.vertexSet
      }

      "type is RemoveEdge" in {
        val g = nonEmptyGraph
        RemoveEdge(g.edgeSet.asScala.head).apply(g)
        g.edgeSet should have size 0
        g.vertexSet shouldBe nonEmptyGraph.vertexSet
      }

      "type is RemoveEdgeByVertices" in {
        val g = nonEmptyGraph
        RemoveEdgeByVertices(v1, v2).apply(g)
        g.edgeSet should have size 0
        g.vertexSet shouldBe nonEmptyGraph.vertexSet
      }

      "type is DoNothing" in {
        DoNothing().apply(emptyGraph) shouldBe emptyGraph
        DoNothing().apply(nonEmptyGraph).vertexSet shouldBe nonEmptyGraph.vertexSet
        DoNothing().apply(nonEmptyGraph).edgeSet should have size 1
      }
    }
  }
}

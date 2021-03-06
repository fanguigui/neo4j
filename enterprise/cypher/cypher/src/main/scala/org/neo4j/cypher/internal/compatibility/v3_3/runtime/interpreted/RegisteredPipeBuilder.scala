/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compatibility.v3_3.runtime.interpreted

import org.neo4j.cypher.internal.compatibility.v3_3.runtime.commands.convert.ExpressionConverters
import org.neo4j.cypher.internal.compatibility.v3_3.runtime.commands.{expressions => commandExpressions}
import org.neo4j.cypher.internal.compatibility.v3_3.runtime.interpreted.pipes.{AllNodesScanRegisterPipe, ExpandAllRegisterPipe, NodeIndexSeekRegisterPipe, ProduceResultRegisterPipe, _}
import org.neo4j.cypher.internal.compatibility.v3_3.runtime.interpreted.{expressions => runtimeExpressions}
import org.neo4j.cypher.internal.compatibility.v3_3.runtime.pipes.{LazyLabel, LazyTypes, Pipe, _}
import org.neo4j.cypher.internal.compatibility.v3_3.runtime.{LongSlot, PipeBuilder, PipeExecutionBuilderContext, PipelineInformation}
import org.neo4j.cypher.internal.compiler.v3_3.planDescription.Id
import org.neo4j.cypher.internal.compiler.v3_3.planner.logical.plans._
import org.neo4j.cypher.internal.compiler.v3_3.spi.PlanContext
import org.neo4j.cypher.internal.frontend.v3_3.phases.Monitors
import org.neo4j.cypher.internal.frontend.v3_3.symbols._
import org.neo4j.cypher.internal.frontend.v3_3.{SemanticTable, ast => frontEndAst}
import org.neo4j.cypher.internal.ir.v3_3.IdName

class RegisteredPipeBuilder(fallback: PipeBuilder,
                            expressionConverter: ExpressionConverters,
                            idMap: Map[LogicalPlan, Id],
                            monitors: Monitors,
                            pipelines: Map[LogicalPlan, PipelineInformation],
                            readOnly : Boolean,
                            buildExpression: (frontEndAst.Expression) => frontEndAst.Expression)
                           (implicit context: PipeExecutionBuilderContext, planContext: PlanContext) extends PipeBuilder {

  private val convertExpressions = buildExpression andThen expressionConverter.toCommandExpression

  override def build(plan: LogicalPlan): Pipe = {
    implicit val table: SemanticTable = context.semanticTable

    val id = idMap.getOrElse(plan, new Id)
    val pipelineInformation = pipelines(plan)

    plan match {
      case p@AllNodesScan(IdName(column), _ /*TODO*/) =>
        AllNodesScanRegisterPipe(column, pipelineInformation)(id)

      case p@NodeIndexSeek(IdName(column),label,propertyKeys,valueExpr, _ /*TODO*/) =>
        val indexSeekMode = IndexSeekModeFactory(unique = false, readOnly = readOnly).fromQueryExpression(valueExpr)
        NodeIndexSeekRegisterPipe(column, label, propertyKeys,
          valueExpr.map(convertExpressions), indexSeekMode,pipelineInformation)(id = id)

      case p@NodeByLabelScan(IdName(column), label, _ /*TODO*/) =>
        NodesByLabelScanRegisterPipe(column, LazyLabel(label), pipelineInformation)(id)

      case _ => fallback.build(plan)
    }
  }

  override def build(plan: LogicalPlan, source: Pipe): Pipe = {
    implicit val table: SemanticTable = context.semanticTable

    val id = idMap.getOrElse(plan, new Id)
    val pipelineInformation = pipelines(plan)

    plan match {
      case p@ProduceResult(columns, _) =>
        val runtimeColumns = createProjectionsForResult(columns, pipelineInformation)
        ProduceResultRegisterPipe(source, runtimeColumns)(id)

      case e@Expand(s, IdName(from), dir, types, IdName(to), IdName(relName), ExpandAll) =>
        val fromOffset = pipelineInformation.getLongOffsetFor(from)
        val relOffset = pipelineInformation.getLongOffsetFor(relName)
        val toOffset = pipelineInformation.getLongOffsetFor(to)
        ExpandAllRegisterPipe(source, fromOffset, relOffset, toOffset, dir, LazyTypes(types), pipelineInformation)(id)

      case e@Expand(s, IdName(from), dir, types, IdName(to), IdName(relName), ExpandInto) =>
        val fromSlot = pipelineInformation.get(from).get
        val relSlot = pipelineInformation.get(relName).get
        val toSlot = pipelineInformation.get(to).get
        ExpandIntoRegisterPipe(source, fromSlot, relSlot, toSlot, dir, LazyTypes(types), pipelineInformation)(id)

      case _ => fallback.build(plan, source)
    }
  }

  private def createProjectionsForResult(columns: Seq[String], pipelineInformation1: PipelineInformation) = {
    val runtimeColumns: Seq[(String, commandExpressions.Expression)] = columns map {
      k =>
        pipelineInformation1(k) match {
          case LongSlot(offset, false, CTNode, _) =>
            k -> runtimeExpressions.NodeFromRegister(offset)
        }
    }
    runtimeColumns
  }

  override def build(plan: LogicalPlan, lhs: Pipe, rhs: Pipe): Pipe =
    plan match {
      case _ => fallback.build(plan, lhs, rhs)
    }
}

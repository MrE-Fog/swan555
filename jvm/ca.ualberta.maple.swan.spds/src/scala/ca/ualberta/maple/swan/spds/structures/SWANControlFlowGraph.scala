/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

package ca.ualberta.maple.swan.spds.structures

import java.util
import boomerang.scene.{ControlFlowGraph, Statement}
import ca.ualberta.maple.swan.ir
import ca.ualberta.maple.swan.ir.{CanOperatorDef, Constants, Operator, SymbolRef, Terminator, Type}
import ca.ualberta.maple.swan.spds.structures.SWANControlFlowGraph.SWANBlock
import com.google.common.collect.{HashMultimap, Lists, Maps, Multimap}
import org.jgrapht.Graph
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IterableHasAsScala

class SWANControlFlowGraph(val method: SWANMethod) extends ControlFlowGraph {

  // Map from CanOperator(Def) or CanTerminator(Def) to Statement
  val mappedStatements: util.HashMap[Object, Statement] = Maps.newHashMap

  private val startPointCache: util.List[Statement] = Lists.newArrayList
  private val endPointCache: util.List[Statement] = Lists.newArrayList
  private val succsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val predsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val statements: java.util.List[Statement] = Lists.newArrayList

  // These mappings use up a lot of space, but are necessary for quick lookup.
  val blocks = new mutable.HashMap[SWANStatement, SWANBlock]
  val stmtToBlock = new mutable.HashMap[SWANStatement, SWANBlock]
  val exitBlocks = new mutable.HashSet[SWANBlock]
  private val graph: Graph[SWANBlock, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])

  {
    // TOD0: dedicated NOP instruction?
    val nopSymbol = new ir.Symbol(new SymbolRef("nop"), new Type("Any"))
    val opDef = new CanOperatorDef(Operator.neww(nopSymbol, nopSymbol.tpe), None)
    val firstBlock = method.delegate.blocks(0)
    val startInst = if (firstBlock.operators.isEmpty) firstBlock.terminator else firstBlock.operators(0)
    val srcMap = method.moduleGroup.swirlSourceMap
    if (srcMap.nonEmpty) {
      srcMap.get.put(opDef, srcMap.get(startInst))
    }
    firstBlock.operators.insert(0, opDef)
    method.allValues.put("nop", SWANVal.Simple(nopSymbol, method))
    method.newValues.put("nop", SWANVal.NewExpr(nopSymbol, method))
  }

  // Iterate through delegate blocks
  method.delegate.blocks.foreach(b => {
    var startStatement: SWANStatement = null
    val blockStatements = new ArrayBuffer[SWANStatement]()

    // Convert Operators to statements
    b.operators.foreach(op => {
      val statement: SWANStatement = {
        op.operator match {
          case operator: Operator.neww => SWANStatement.Allocation(op, operator, method)
          case operator: Operator.assign => SWANStatement.Assign(op, operator, method)
          case operator: Operator.literal => SWANStatement.Literal(op, operator, method)
          case operator: Operator.dynamicRef => SWANStatement.DynamicFunctionRef(op, operator, method)
          case operator: Operator.builtinRef => SWANStatement.BuiltinFunctionRef(op, operator, method)
          case operator: Operator.functionRef => SWANStatement.FunctionRef(op, operator, method)
          case operator: Operator.apply => SWANStatement.ApplyFunctionRef(op, operator, method)
          case operator: Operator.singletonRead => SWANStatement.StaticFieldLoad(op, operator, method)
          case operator: Operator.singletonWrite => SWANStatement.StaticFieldStore(op, operator, method)
          case operator: Operator.fieldRead => SWANStatement.FieldLoad(op, operator, method)
          case operator: Operator.fieldWrite => SWANStatement.FieldWrite(op, operator, method)
          case operator: Operator.condFail => SWANStatement.ConditionalFatalError(op, operator, method)
        }
      }
      if (startStatement == null) startStatement = statement
      blockStatements.append(statement)
      mappedStatements.put(op, statement)
      mappedStatements.put(op.operator, statement)
      statements.add(statement)
    })

    // Convert Terminator to statement
    val termStatement: SWANStatement = {
      val term = b.terminator
      val statement = b.terminator.terminator match {
        case terminator: Terminator.br_can => SWANStatement.Branch(term, terminator, method)
        case terminator: Terminator.brIf_can => SWANStatement.ConditionalBranch(term, terminator, method)
        case terminator: Terminator.ret => SWANStatement.Return(term, terminator, method)
        case Terminator.unreachable => SWANStatement.Unreachable(term, method)
        case terminator: Terminator.yld => SWANStatement.Yield(term, terminator, method)
      }
      if (startStatement == null) startStatement = statement
      blockStatements.append(statement)
      statement
    }

    // Create Block
    val newBlock = new SWANBlock(b.blockRef.label, blockStatements)
    blocks.put(startStatement, newBlock)
    blockStatements.foreach(s => stmtToBlock.put(s, newBlock))
    exitBlocks.add(newBlock)
    graph.addVertex(newBlock)
    mappedStatements.put(b.terminator, termStatement)
    mappedStatements.put(b.terminator.terminator, termStatement)
    statements.add(termStatement)
  })

  // Add first block's first statement to startPointCache
  if (method.delegate.blocks(0).operators.nonEmpty){
    startPointCache.add(
      mappedStatements.get(method.delegate.blocks(0).operators(0)))
  } else {
    startPointCache.add(
      mappedStatements.get(method.delegate.blocks(0).terminator))
  }

  // Iterate through all delegate blocks again for succs/preds
  method.delegate.blocks.foreach(b => {
    // succs/preds for block statements
    var prev: Statement = null
    b.operators.foreach(op => {
      val curr = mappedStatements.get(op)
      if (prev != null) {
        succsOfCache.put(prev, curr)
        predsOfCache.put(curr, prev)
      }
      prev = curr
    })
    // succs/prods for last block statement and terminator
    val term = mappedStatements.get(b.terminator)
    if (prev != null) {
      succsOfCache.put(prev, term)
      predsOfCache.put(term, prev)
    }
    // get block for next step
    val block: SWANBlock = {
      val fstStmt: Object = {
        if (b.operators.nonEmpty) {
          b.operators(0)
        } else {
          b.terminator
        }
      }
      blocks(mappedStatements.get(fstStmt).asInstanceOf[SWANStatement])
    }
    // succs/prods for inter-block
    method.delegate.cfg.outgoingEdgesOf(b).forEach(e => {
      val target = method.delegate.cfg.getEdgeTarget(e)
      if (target.blockRef.label == Constants.exitBlock) {
        // delegate exit blocks have no statements or terminators
        endPointCache.add(term)
        exitBlocks.add(block)
      } else {
        val targetStatement = mappedStatements.get({
          if (target.operators.nonEmpty) {
            target.operators(0)
          } else {
            target.terminator
          }
        })
        succsOfCache.put(term, targetStatement)
        predsOfCache.put(targetStatement, term)
        val targetBlock = blocks(targetStatement.asInstanceOf[SWANStatement])
        graph.addEdge(block, targetBlock)
      }
    })
  })

  override def getStartPoints: util.Collection[Statement] = startPointCache

  override def getEndPoints: util.Collection[Statement] = endPointCache

  override def getSuccsOf(statement: Statement): util.Collection[Statement] = succsOfCache.get(statement)

  override def getPredsOf(statement: Statement): util.Collection[Statement] = predsOfCache.get(statement)

  override def getStatements: java.util.List[Statement] = statements

  def getBlockSuccsOf(b: SWANBlock): Set[SWANBlock] = {
    graph.outgoingEdgesOf(b).asScala.map(e => graph.getEdgeTarget(e)).toSet
  }

  def getBlockPredsOf(b: SWANBlock): Set[SWANBlock] = {
    graph.incomingEdgesOf(b).asScala.map(e => graph.getEdgeTarget(e)).toSet
  }

}

object SWANControlFlowGraph {
  class SWANBlock(val name: String, val stmts: ArrayBuffer[SWANStatement]) {
    def method: SWANMethod = stmts(0).getSWANMethod
    def succs: Set[SWANBlock] = method.getCFG.getBlockSuccsOf(this)
    def preds: Set[SWANBlock] = method.getCFG.getBlockPredsOf(this)
    def isExitBlock: Boolean = method.getCFG.exitBlocks.contains(this)
  }
}

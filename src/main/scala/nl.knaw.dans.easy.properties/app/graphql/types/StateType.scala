/**
 * Copyright (C) 2019 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.properties.app.graphql.types

import nl.knaw.dans.easy.properties.app.graphql.DataContext
import nl.knaw.dans.easy.properties.app.graphql.relay.ExtendedConnection
import nl.knaw.dans.easy.properties.app.model.SeriesFilter.SeriesFilter
import nl.knaw.dans.easy.properties.app.model.state.StateLabel.StateLabel
import nl.knaw.dans.easy.properties.app.model.state._
import nl.knaw.dans.easy.properties.app.model.{ Deposit, DepositId, SeriesFilter, Timestamp }
import sangria.execution.deferred.{ Fetcher, HasId }
import sangria.macros.derive._
import sangria.marshalling.FromInput._
import sangria.marshalling.{ CoercedScalaResultMarshaller, FromInput, ResultMarshaller }
import sangria.relay._
import sangria.schema.{ Argument, Context, EnumType, Field, InputObjectType, ObjectType, OptionInputType, OptionType }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait StateType {
  this: DepositType with NodeType with MetaTypes with Scalars =>

  implicit val StateLabelType: EnumType[StateLabel.Value] = deriveEnumType(
    EnumTypeDescription("The label identifying the state of a deposit."),
    DocumentValue("DRAFT", "Open for additional data."),
    DocumentValue("UPLOADED", "Deposit uploaded, waiting to be finalized."),
    DocumentValue("FINALIZING", "Closed and being checked for validity."),
    DocumentValue("INVALID", "Does not contain a valid bag."),
    DocumentValue("SUBMITTED", "Valid and waiting for processing by easy-ingest-flow, or being processed in it."),
    DocumentValue("IN_REVIEW", "Currently undergoing curation by the datamanagers."),
    DocumentValue("REJECTED", "Did not meet the requirements set by easy-ingest-flow for this type of deposit."),
    DocumentValue("FAILED", "Failed to be archived because of some unexpected condition. It may be possible to manually fix this."),
    DocumentValue("FEDORA_ARCHIVED", "Was successfully archived in the Fedora Archive."),
    DocumentValue("ARCHIVED", "Was successfully archived in the data vault."),
  )

  implicit val currentStateHasId: HasId[(DepositId, Option[State]), DepositId] = HasId { case (id, _) => id }
  implicit val allStatesHasId: HasId[(DepositId, Seq[State]), DepositId] = HasId { case (id, _) => id }

  val fetchCurrentStates = Fetcher((ctx: DataContext, ids: Seq[DepositId]) => Future {
    ids match {
      case Seq() => Seq.empty
      case Seq(id) => Seq(id -> ctx.deposits.getCurrentState(id))
      case _ => ctx.deposits.getCurrentStates(ids)
    }
  })
  val fetchAllStates = Fetcher((ctx: DataContext, ids: Seq[DepositId]) => Future {
    ids match {
      case Seq() => Seq.empty
      case Seq(id) => Seq(id -> ctx.deposits.getAllStates(id))
      case _ => ctx.deposits.getAllStates(ids)
    }
  })

  implicit val DepositStateFilterType: InputObjectType[DepositStateFilter] = deriveInputObjectType(
    InputObjectTypeDescription("The label and filter to be used in searching for deposits by state"),
    DocumentInputField("label", "If provided, only show deposits with this state."),
    DocumentInputField("filter", "Determine whether to search in current states (`LATEST`, default) or all current and past states (`ALL`)."),
  )
  implicit val DepositStateFilterFromInput: FromInput[DepositStateFilter] = new FromInput[DepositStateFilter] {
    val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    def fromResult(node: marshaller.Node): DepositStateFilter = {
      val ad = node.asInstanceOf[Map[String, Any]]

      DepositStateFilter(
        label = ad("label").asInstanceOf[StateLabel],
        filter = ad("filter").asInstanceOf[Option[SeriesFilter]].getOrElse(SeriesFilter.LATEST),
      )
    }
  }

  private val seriesFilterArgument: Argument[SeriesFilter] = Argument(
    name = "stateFilter",
    argumentType = SeriesFilterType,
    description = Some("Determine whether to search in current states (`LATEST`, default) or all current and past states (`ALL`)."),
    defaultValue = Some(SeriesFilter.LATEST -> SeriesFilterToInput),
    fromInput = coercedScalaInput,
    astDirectives = Vector.empty,
    astNodes = Vector.empty,
  )
  val depositStateFilterArgument: Argument[Option[DepositStateFilter]] = {
    Argument(
      name = "state",
      argumentType = OptionInputType(DepositStateFilterType),
      description = Some("List only those deposits that have this specified state label."),
      defaultValue = None,
      fromInput = optionInput(inputObjectResultInput(DepositStateFilterFromInput)),
      astDirectives = Vector.empty,
      astNodes = Vector.empty,
    )
  }

  private val depositField: Field[DataContext, State] = Field(
    name = "deposit",
    description = Some("Returns the deposit that is associated with this particular ingest step"),
    fieldType = OptionType(DepositType),
    resolve = getDepositByState,
  )
  private val depositsField: Field[DataContext, State] = Field(
    name = "deposits",
    description = Some("List all deposits with the same current state label."),
    arguments = List(
      seriesFilterArgument,
      optDepositOrderArgument,
    ) ++ Connection.Args.All,
    fieldType = OptionType(depositConnectionType),
    resolve = ctx => ExtendedConnection.connectionFromSeq(getDeposits(ctx), ConnectionArgs(ctx)),
  )

  private def getDepositByState(context: Context[DataContext, State]): Option[Deposit] = {
    val repository = context.ctx.deposits

    val stateId = context.value.id

    repository.getDepositByStateId(stateId)
  }

  private def getDeposits(context: Context[DataContext, State]): Seq[Deposit] = {
    val repository = context.ctx.deposits

    val label = context.value.label
    val stateFilter = context.arg(seriesFilterArgument)
    val orderBy = context.arg(optDepositOrderArgument)

    val result = repository.getDeposits(
      stateFilter = Some(DepositStateFilter(label, stateFilter))
    )

    orderBy.fold(result)(order => result.sorted(order.ordering))
  }

  implicit object StateIdentifiable extends Identifiable[State] {
    override def id(state: State): String = state.id
  }

  implicit val StateType: ObjectType[DataContext, State] = deriveObjectType(
    ObjectTypeDescription("The state of the deposit."),
    Interfaces[DataContext, State](nodeInterface),
    ExcludeFields("id"),
    DocumentField("label", "The state label of the deposit."),
    DocumentField("description", "Additional information about the state."),
    DocumentField("timestamp", "The timestamp at which the deposit got into this state."),
    AddFields(
      Node.globalIdField[DataContext, State],
      depositField,
      depositsField,
    ),
  )

  val ConnectionDefinition(_, stateConnectionType) = ExtendedConnection.definition[DataContext, ExtendedConnection, State](
    name = "State",
    nodeType = StateType,
  )

  implicit val InputStateType: InputObjectType[InputState] = deriveInputObjectType(
    InputObjectTypeName("InputState"),
    InputObjectTypeDescription("The state of a deposit"),
    DocumentInputField("label", "The state label of the deposit."),
    DocumentInputField("description", "Additional information about the state."),
    DocumentInputField("timestamp", "The timestamp at which the deposit got into this state."),
  )
  implicit val InputStateFromInput: FromInput[InputState] = new FromInput[InputState] {
    override val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): InputState = {
      val ad = node.asInstanceOf[Map[String, Any]]

      InputState(
        label = ad("label").asInstanceOf[StateLabel],
        description = ad("description").asInstanceOf[String],
        timestamp = ad("timestamp").asInstanceOf[Timestamp],
      )
    }
  }
}

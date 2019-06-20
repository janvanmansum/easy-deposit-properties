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
import nl.knaw.dans.easy.properties.app.model.identifier.{ Identifier, IdentifierType }
import nl.knaw.dans.easy.properties.app.model.{ Deposit, DepositId, DepositorId }
import nl.knaw.dans.easy.properties.app.repository.DepositFilters
import sangria.marshalling.FromInput.coercedScalaInput
import sangria.relay.{ Connection, ConnectionArgs }
import sangria.schema.{ Argument, Context, DeferredValue, Field, ObjectType, OptionInputType, OptionType, StringType, fields }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait QueryType {
  this: DepositType
    with DepositorType
    with StateType
    with IngestStepType
    with IdentifierGraphQLType
    with DoiEventTypes
    with CuratorType
    with CurationEventType
    with ContentTypeGraphQLType
    with TimebasedSearch
    with MetaTypes
    with NodeType
    with Scalars =>

  private val depositorIdArgument: Argument[Option[DepositorId]] = Argument(
    name = "id",
    description = Some("If provided, only show deposits from this depositor."),
    defaultValue = None,
    argumentType = OptionInputType(StringType),
    fromInput = coercedScalaInput,
    astDirectives = Vector.empty,
    astNodes = Vector.empty,
  )
  private val depositIdArgument: Argument[DepositId] = Argument(
    name = "id",
    description = Some("The id for which to find the deposit"),
    defaultValue = None,
    argumentType = UUIDType,
    fromInput = coercedScalaInput,
    astDirectives = Vector.empty,
    astNodes = Vector.empty,
  )
  private val identifierTypeArgument: Argument[IdentifierType.Value] = Argument(
    name = "type",
    description = Some("The type of identifier to be found."),
    defaultValue = None,
    argumentType = IdentifierTypeType,
    fromInput = coercedScalaInput,
    astDirectives = Vector.empty,
    astNodes = Vector.empty,
  )
  private val identifierValueArgument: Argument[String] = Argument(
    name = "value",
    description = Some("The value of the identifier to be found."),
    defaultValue = None,
    argumentType = StringType,
    fromInput = coercedScalaInput,
    astDirectives = Vector.empty,
    astNodes = Vector.empty,
  )

  private val depositField: Field[DataContext, Unit] = Field(
    name = "deposit",
    description = Some("Get the technical metadata of the deposit identified by 'id'."),
    arguments = List(depositIdArgument),
    fieldType = OptionType(DepositType),
    resolve = getDeposit,
  )
  private val depositsField: Field[DataContext, Unit] = Field(
    name = "deposits",
    description = Some("List all registered deposits."),
    arguments = List(
      depositStateFilterArgument,
      depositIngestStepFilterArgument,
      depositDoiRegisteredFilterArgument,
      depositDoiActionFilterArgument,
      depositCuratorFilterArgument,
      depositIsNewVersionFilterArgument,
      depositCurationRequiredFilterArgument,
      depositCurationPerformedFilterArgument,
      depositContentTypeFilterArgument,
      optDepositOrderArgument,
    ) ::: timebasedSearchArguments ::: Connection.Args.All,
    fieldType = OptionType(depositConnectionType),
    resolve = ctx => getDeposits(ctx).map(ExtendedConnection.connectionFromSeq(_, ConnectionArgs(ctx))),
  )
  private val depositorField: Field[DataContext, Unit] = Field(
    name = "depositor",
    description = Some("Get the technical metadata related to this depositor."),
    arguments = List(
      depositorIdArgument,
    ),
    fieldType = OptionType(DepositorType),
    resolve = getDepositor,
  )
  private val identifierField: Field[DataContext, Unit] = Field(
    name = "identifier",
    description = Some("Find an identifier with the given type and value."),
    arguments = List(
      identifierTypeArgument,
      identifierValueArgument,
    ),
    fieldType = OptionType(IdentifierObjectType),
    resolve = getIdentifier,
  )

  private def getDeposit(context: Context[DataContext, Unit]): Try[Deposit] = {
    context.ctx.deposits
      .getDeposit(context.arg(depositIdArgument))
      .toTry
  }

  private def getDeposits(context: Context[DataContext, Unit]): DeferredValue[DataContext, Seq[Deposit]] = {
    DeferredValue(depositsFetcher.defer(DepositFilters(
      stateFilter = context.arg(depositStateFilterArgument),
      ingestStepFilter = context.arg(depositIngestStepFilterArgument),
      doiRegisteredFilter = context.arg(depositDoiRegisteredFilterArgument),
      doiActionFilter = context.arg(depositDoiActionFilterArgument),
      curatorFilter = context.arg(depositCuratorFilterArgument),
      isNewVersionFilter = context.arg(depositIsNewVersionFilterArgument),
      curationRequiredFilter = context.arg(depositCurationRequiredFilterArgument),
      curationPerformedFilter = context.arg(depositCurationPerformedFilterArgument),
      contentTypeFilter = context.arg(depositContentTypeFilterArgument),
    ))).map { case (_, deposits) => timebasedFilterAndSort(context, optDepositOrderArgument, deposits) }
  }

  private def getDepositor(context: Context[DataContext, Unit]): Option[DepositorId] = {
    context.arg(depositorIdArgument)
  }

  private def getIdentifier(context: Context[DataContext, Unit]): Try[Option[Identifier]] = {
    context.ctx.deposits
      .getIdentifier(
        idType = context.arg(identifierTypeArgument),
        idValue = context.arg(identifierValueArgument),
      )
      .toTry
  }

  implicit val QueryType: ObjectType[DataContext, Unit] = ObjectType(
    name = "Query",
    description = "The query root of easy-deposit-properties' GraphQL interface.",
    fields = fields[DataContext, Unit](
      depositField,
      depositsField,
      depositorField,
      identifierField,
      nodeField,
      nodesField,
    ),
  )
}

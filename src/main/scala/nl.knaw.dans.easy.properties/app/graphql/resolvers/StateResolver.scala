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
package nl.knaw.dans.easy.properties.app.graphql.resolvers

import nl.knaw.dans.easy.properties.app.graphql.DataContext
import nl.knaw.dans.easy.properties.app.model.DepositId
import nl.knaw.dans.easy.properties.app.model.state.State
import sangria.schema.DeferredValue

object StateResolver {

  lazy val currentStatesFetcher: CurrentFetcher[State] = fetchCurrent(_.repo.states.getCurrent)
  lazy val allStatesFetcher: AllFetcher[State] = fetchAll(_.repo.states.getAll)

  def currentById(depositId: DepositId)(implicit ctx: DataContext): DeferredValue[DataContext, Option[State]] = {
    DeferredValue(currentStatesFetcher.defer(depositId))
      .map { case (_, optState) => optState }
  }

  def allById(depositId: DepositId)(implicit ctx: DataContext): DeferredValue[DataContext, Seq[State]] = {
    DeferredValue(allStatesFetcher.defer(depositId))
      .map { case (_, states) => states }
  }
}

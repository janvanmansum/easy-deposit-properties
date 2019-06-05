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
package nl.knaw.dans.easy.properties.app.graphql

import nl.knaw.dans.easy.properties.app.model.DepositId
import nl.knaw.dans.easy.properties.app.model.identifier.IdentifierType.IdentifierType
import sangria.execution.UserFacingError

package object error {

  abstract class MutationError(val msg: String) extends Exception(msg) with UserFacingError
  case class NoSuchDepositError(depositId: DepositId) extends MutationError(s"Deposit $depositId does not exist.")
  case class DepositAlreadyExistsError(depositId: DepositId) extends MutationError(s"Deposit $depositId already exist.")
  case class IdentifierAlreadyExistsError(depositId: DepositId, identifierType: IdentifierType) extends MutationError(s"Identifier $identifierType already exists for $depositId.")
}
